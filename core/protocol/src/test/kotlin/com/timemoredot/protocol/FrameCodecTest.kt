package com.timemoredot.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameCodecTest {

    @Test
    fun setNameTruncatesAtCodePointBoundary() {
        val bytes = (FrameCodec.encode(ScaleCommand.SetName("커피저울한글이름")) as EncodeResult.Ok).bytes
        val payload = bytes.copyOfRange(6, bytes.size - 2)
        assertEquals(15, payload.size)
        assertEquals("커피저울한", payload.decodeToString())
        assertEquals("000F", bytes.copyOfRange(4, 6).hex())
    }

    @Test
    fun emptyNameIsInvalid() {
        assertEquals(EncodeResult.InvalidName, FrameCodec.encode(ScaleCommand.SetName("")))
    }

    @Test
    fun responseKeysPairRequestsWithResponses() {
        assertEquals(ResponseKey(0x03, 0x0D), ScaleCommand.Tare.responseKey())
        assertEquals(ScaleCommand.Tare.responseKey(), ScaleFrame.WriteAck(0x0D, true).responseKey())
        assertEquals(ScaleCommand.ReadUnit.responseKey(), ScaleFrame.UnitReport(Op.Read, 0).responseKey())
        assertEquals(ScaleCommand.ReadName.responseKey(), ScaleFrame.DeviceName("x").responseKey())
        assertEquals(ScaleCommand.ReadFirmware.responseKey(), ScaleFrame.FirmwareVersion("v").responseKey())
        assertEquals(null, ScaleFrame.UnitReport(Op.Notify, 0).responseKey())
        assertEquals(null, ScaleFrame.WeightReport(0, 0, 0, false).responseKey())
        assertEquals(null, ScaleFrame.Unknown(1, 4, "00").responseKey())
    }

    @Test
    fun writeAckPayloadOtherThan01IsFailure() {
        assertEquals(
            listOf(DecodeResult.Frame(ScaleFrame.WriteAck(0x0D, false))),
            FrameReassembler().feed("A55A030D0001020000".hex()),
        )
    }

    @Test
    fun invalidUtf8NameAndNonAsciiFirmwareUseReplacementChar() {
        assertEquals(
            listOf(
                DecodeResult.Frame(ScaleFrame.DeviceName("A�")),
                DecodeResult.Frame(ScaleFrame.FirmwareVersion("v�")),
            ),
            FrameReassembler().feed("A55A020C000241FF0000A55A02100002768E0000".hex()),
        )
    }
}
