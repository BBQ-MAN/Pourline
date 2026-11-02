package com.timemoredot.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** docs/spec/protocol-vectors.json. 경로는 build.gradle.kts가 시스템 속성으로 넘긴다. */
object Vectors {
    val root: JsonObject by lazy {
        val path = System.getProperty("protocol.vectors") ?: error("protocol.vectors 시스템 속성 없음")
        Json.parseToJsonElement(File(path).readText()).jsonObject
    }
    val crc: JsonObject get() = root.getValue("crc").jsonObject
    val all: List<JsonObject> get() = root.getValue("vectors").jsonArray.map { it.jsonObject }
    fun byId(id: String): JsonObject = all.single { it.str("id") == id }
}

fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content
private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

fun String.hex(): ByteArray = hexToByteArray()
fun ByteArray.hex(): String = toHexString(HexFormat.UpperCase)

/** hex가 문자열이면 한 개, 배열이면 알림 조각 목록. null이면 빈 목록. */
fun JsonObject.hexChunks(): List<String> = when (val h = getValue("hex")) {
    JsonNull -> emptyList()
    is kotlinx.serialization.json.JsonArray -> h.map { it.jsonPrimitive.content }
    else -> listOf(h.jsonPrimitive.content)
}

/** rx expect(객체 하나 또는 배열)를 DecodeResult 목록으로. */
fun JsonObject.expectedResults(): List<DecodeResult> = when (val e: JsonElement = getValue("expect")) {
    is kotlinx.serialization.json.JsonArray -> e.map { it.jsonObject.toResult() }
    else -> listOf(e.jsonObject.toResult())
}

private fun op(e: JsonObject) = when (e.str("op")) {
    "notify" -> Op.Notify
    "read" -> Op.Read
    else -> error("op ${e.str("op")}")
}

private fun JsonObject.toResult(): DecodeResult {
    if ("error" in this) {
        check(str("error") == "LengthMismatch") { "알 수 없는 오류 ${str("error")}" }
        return DecodeResult.Error(DecodeError.LengthMismatch(int("cmd"), int("expectedLen"), int("actualLen")))
    }
    val frame = when (val t = str("type")) {
        "WeightReport" -> ScaleFrame.WeightReport(
            int("weightRaw"), int("flowRaw"), int("timerSec"), getValue("overload").jsonPrimitive.boolean,
        )
        "TimerState" -> ScaleFrame.TimerState(op(this), int("stateRaw"))
        "Battery" -> ScaleFrame.Battery(op(this), int("bars"), int("percent"))
        "Unit" -> ScaleFrame.UnitReport(op(this), int("unitRaw"))
        "WriteAck" -> ScaleFrame.WriteAck(int("cmd"), getValue("success").jsonPrimitive.boolean)
        "DeviceName" -> ScaleFrame.DeviceName(str("name"))
        "FirmwareVersion" -> ScaleFrame.FirmwareVersion(str("version"))
        "Unknown" -> ScaleFrame.Unknown(int("opcode"), int("cmd"), str("payloadHex"))
        else -> error("알 수 없는 expect.type $t")
    }
    return DecodeResult.Frame(frame)
}

/** tx command 객체를 ScaleCommand로. */
fun JsonObject.command(): ScaleCommand {
    val c = getValue("command").jsonObject
    return when (val t = c.str("type")) {
        "ReadTimer" -> ScaleCommand.ReadTimer
        "SetTimer" -> ScaleCommand.SetTimer(
            when (c.str("action")) {
                "start" -> TimerAction.Start
                "pause" -> TimerAction.Pause
                "reset" -> TimerAction.Reset
                else -> error("action ${c.str("action")}")
            },
        )
        "ReadBattery" -> ScaleCommand.ReadBattery
        "ReadUnit" -> ScaleCommand.ReadUnit
        "SetUnit" -> ScaleCommand.SetUnit(
            when (c.str("unit")) {
                "g" -> WeightUnit.Gram
                "oz" -> WeightUnit.Ounce
                else -> error("unit ${c.str("unit")}")
            },
        )
        "PowerOff" -> ScaleCommand.PowerOff
        "ReadName" -> ScaleCommand.ReadName
        "SetName" -> ScaleCommand.SetName(c.str("name"))
        "Tare" -> ScaleCommand.Tare
        "ReadFirmware" -> ScaleCommand.ReadFirmware
        "FactoryReset" -> ScaleCommand.FactoryReset
        "ForgetDevice" -> ScaleCommand.ForgetDevice
        else -> error("알 수 없는 command.type $t")
    }
}

/** tx expect: hex가 있으면 그 바이트, null이면 expect.error(InvalidName). */
fun JsonObject.expectedEncode(): EncodeResult {
    val chunks = hexChunks()
    if (chunks.isNotEmpty()) return EncodeResult.Ok(chunks.single().hex())
    check(getValue("expect").jsonObject.str("error") == "InvalidName")
    return EncodeResult.InvalidName
}
