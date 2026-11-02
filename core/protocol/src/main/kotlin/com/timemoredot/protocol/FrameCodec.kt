package com.timemoredot.protocol

object FrameCodec {
    /** 프레임 끝에 TX_CRC 2바이트를 항상 붙인다(F-9, F-12: CRC 없는 7바이트 프레임 금지). SetName은 16바이트 초과 시 코드 포인트 경계에서 자름 (C-0C-2) */
    fun encode(command: ScaleCommand): EncodeResult {
        val payload = when (command) {
            is ScaleCommand.SetTimer -> byteArrayOf(command.action.raw.toByte())
            is ScaleCommand.SetUnit -> byteArrayOf(command.unit.raw.toByte())
            is ScaleCommand.SetName -> truncateUtf8(command.name, NAME_MAX_BYTES)
                .takeIf { it.isNotEmpty() } ?: return EncodeResult.InvalidName
            else -> ByteArray(0)
        }
        val key = command.responseKey()
        val head = byteArrayOf(
            HEADER_0, HEADER_1, key.opcode.toByte(), key.cmd.toByte(),
            (payload.size shr 8).toByte(), payload.size.toByte(),
        )
        val crc = byteArrayOf((TX_CRC shr 8).toByte(), TX_CRC.toByte())
        return EncodeResult.Ok(head + payload + crc)
    }

    /** 앞에서부터 코드 포인트를 더해 maxBytes를 넘기 직전까지 UTF-8 바이트를 돌려준다. */
    private fun truncateUtf8(text: String, maxBytes: Int): ByteArray {
        val out = StringBuilder()
        var size = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val chars = String(Character.toChars(cp))
            val n = chars.encodeToByteArray().size
            if (size + n > maxBytes) break
            out.append(chars)
            size += n
            i += Character.charCount(cp)
        }
        return out.toString().encodeToByteArray()
    }

    /** protocol.md 3절. 헤더·길이·CRC는 재조립기가 떼어 낸 뒤 opcode·cmd·payload만 받는다. */
    internal fun decode(opcode: Int, cmd: Int, payload: ByteArray): DecodeResult {
        val expectedLen = when {
            opcode == OP_WRITE && cmd in WRITE_CMDS -> 1
            opcode == OP_READ && cmd in setOf(CMD_NAME, CMD_FIRMWARE) -> null   // 가변 길이: 검사하지 않음(2.3 규칙 7)
            opcode == OP_NOTIFY && cmd == CMD_WEIGHT -> WEIGHT_PAYLOAD_LEN
            (opcode == OP_NOTIFY || opcode == OP_READ) && cmd in setOf(CMD_TIMER, CMD_UNIT) -> 1
            (opcode == OP_NOTIFY || opcode == OP_READ) && cmd == CMD_BATTERY -> 2
            else -> return unknown(opcode, cmd, payload)   // 2.3 규칙 8 (예: 0x04, C-04-1)
        }
        if (expectedLen != null && payload.size != expectedLen) {
            return DecodeResult.Error(DecodeError.LengthMismatch(cmd, expectedLen, payload.size))
        }
        fun u8(i: Int) = payload[i].toInt() and 0xFF
        fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
        val op = if (opcode == OP_READ) Op.Read else Op.Notify
        val frame = when {
            opcode == OP_WRITE -> ScaleFrame.WriteAck(cmd, success = u8(0) == 0x01)   // C-W-1a: 01 밖은 실패
            cmd == CMD_WEIGHT -> ScaleFrame.WeightReport(
                weightRaw = (u16(0) shl 16) or u16(2),
                flowRaw = u16(4).toShort().toInt(),
                timerSec = u16(6),
                overload = u8(8) != 0,
            )
            cmd == CMD_TIMER -> ScaleFrame.TimerState(op, u8(0))
            cmd == CMD_BATTERY -> ScaleFrame.Battery(op, bars = u8(0), percent = u8(1))
            cmd == CMD_UNIT -> ScaleFrame.UnitReport(op, u8(0))
            cmd == CMD_NAME -> ScaleFrame.DeviceName(payload.decodeToString())   // 잘못된 UTF-8은 U+FFFD (C-0C-3)
            else -> ScaleFrame.FirmwareVersion(   // C-10-1: 비ASCII 바이트는 U+FFFD
                payload.map { if (it >= 0) it.toInt().toChar() else '�' }.joinToString(""),
            )
        }
        return DecodeResult.Frame(frame)
    }

    private fun unknown(opcode: Int, cmd: Int, payload: ByteArray) =
        DecodeResult.Frame(ScaleFrame.Unknown(opcode, cmd, payload.toHexString(HexFormat.UpperCase)))
}
