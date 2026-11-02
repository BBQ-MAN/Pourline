package com.timemoredot.protocol

enum class TimerAction(val raw: Int) { Start(0x01), Pause(0x02), Reset(0x03) }  // C-02-1
enum class ScaleTimerState { Running, Paused, Reset }                  // stateRaw 01/02/03
enum class Op { Notify, Read }

sealed interface ScaleCommand {
    data object ReadTimer : ScaleCommand
    data class SetTimer(val action: TimerAction) : ScaleCommand
    data object ReadBattery : ScaleCommand
    data object ReadUnit : ScaleCommand
    data class SetUnit(val unit: WeightUnit) : ScaleCommand
    data object PowerOff : ScaleCommand
    data object ReadName : ScaleCommand
    data class SetName(val name: String) : ScaleCommand
    data object Tare : ScaleCommand
    data object ReadFirmware : ScaleCommand
    data object FactoryReset : ScaleCommand
    data object ForgetDevice : ScaleCommand
}

/** 벡터 expect.type과 1:1. 벡터의 "Unit"은 kotlin.Unit과 겹쳐 UnitReport로 부른다. */
sealed interface ScaleFrame {
    data class WeightReport(val weightRaw: Int, val flowRaw: Int, val timerSec: Int, val overload: Boolean) : ScaleFrame
    data class TimerState(val op: Op, val stateRaw: Int) : ScaleFrame
    data class Battery(val op: Op, val bars: Int, val percent: Int) : ScaleFrame
    data class UnitReport(val op: Op, val unitRaw: Int) : ScaleFrame
    data class WriteAck(val cmd: Int, val success: Boolean) : ScaleFrame
    data class DeviceName(val name: String) : ScaleFrame
    data class FirmwareVersion(val version: String) : ScaleFrame
    data class Unknown(val opcode: Int, val cmd: Int, val payloadHex: String) : ScaleFrame
}

/** 수신 CRC를 검사하지 않으므로 CRC 오류는 없다(F-8). */
sealed interface DecodeError {
    data class LengthMismatch(val cmd: Int, val expectedLen: Int, val actualLen: Int) : DecodeError
}

sealed interface DecodeResult {
    data class Frame(val frame: ScaleFrame) : DecodeResult
    data class Error(val error: DecodeError) : DecodeResult
}

sealed interface EncodeResult {
    data class Ok(val bytes: ByteArray) : EncodeResult {
        override fun equals(other: Any?): Boolean = other is Ok && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
        override fun toString(): String = "Ok(${bytes.toHexString(HexFormat.UpperCase)})"
    }
    data object InvalidName : EncodeResult               // 빈 이름 (C-0C-1)
}

/** 요청과 응답을 잇는 키. Notify(opcode 01) 프레임과 Unknown은 키가 없다. */
data class ResponseKey(val opcode: Int, val cmd: Int)

fun ScaleCommand.responseKey(): ResponseKey = when (this) {
    ScaleCommand.ReadTimer -> ResponseKey(OP_READ, CMD_TIMER)
    is ScaleCommand.SetTimer -> ResponseKey(OP_WRITE, CMD_TIMER)
    ScaleCommand.ReadBattery -> ResponseKey(OP_READ, CMD_BATTERY)
    ScaleCommand.ReadUnit -> ResponseKey(OP_READ, CMD_UNIT)
    is ScaleCommand.SetUnit -> ResponseKey(OP_WRITE, CMD_UNIT)
    ScaleCommand.PowerOff -> ResponseKey(OP_WRITE, CMD_POWER_OFF)
    ScaleCommand.ReadName -> ResponseKey(OP_READ, CMD_NAME)
    is ScaleCommand.SetName -> ResponseKey(OP_WRITE, CMD_NAME)
    ScaleCommand.Tare -> ResponseKey(OP_WRITE, CMD_TARE)
    ScaleCommand.ReadFirmware -> ResponseKey(OP_READ, CMD_FIRMWARE)
    ScaleCommand.FactoryReset -> ResponseKey(OP_WRITE, CMD_FACTORY_RESET)
    ScaleCommand.ForgetDevice -> ResponseKey(OP_WRITE, CMD_FORGET)
}

fun ScaleFrame.responseKey(): ResponseKey? = when (this) {
    is ScaleFrame.WeightReport, is ScaleFrame.Unknown -> null
    is ScaleFrame.TimerState -> readKey(op, CMD_TIMER)
    is ScaleFrame.Battery -> readKey(op, CMD_BATTERY)
    is ScaleFrame.UnitReport -> readKey(op, CMD_UNIT)
    is ScaleFrame.WriteAck -> ResponseKey(OP_WRITE, cmd)
    is ScaleFrame.DeviceName -> ResponseKey(OP_READ, CMD_NAME)
    is ScaleFrame.FirmwareVersion -> ResponseKey(OP_READ, CMD_FIRMWARE)
}

private fun readKey(op: Op, cmd: Int): ResponseKey? = if (op == Op.Read) ResponseKey(OP_READ, cmd) else null
