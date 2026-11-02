package com.timemoredot.app.ui.connect

import com.timemoredot.protocol.ScanCandidate

/**
 * architecture.md 9.2. resort=false: 이미 보인 항목은 자리를 지키고(값만 최신), 새 최근 연결 기기는 맨 위, 그 밖의 새 항목은 끝.
 * resort=true: 최근 연결 기기를 맨 위로, 나머지는 RSSI 내림차순(같으면 들어온 순서).
 */
fun orderCandidates(
    previousOrder: List<String>,
    candidates: List<ScanCandidate>,
    recentAddress: String?,
    resort: Boolean,
): List<ScanCandidate> {
    if (resort) {
        val (recent, rest) = candidates.partition { it.address == recentAddress }
        return recent + rest.sortedByDescending { it.rssi }
    }
    val byAddress = candidates.associateBy { it.address }
    val kept = previousOrder.mapNotNull { byAddress[it] }
    val keptAddresses = kept.mapTo(HashSet()) { it.address }
    val (newRecent, newOthers) = candidates.filter { it.address !in keptAddresses }.partition { it.address == recentAddress }
    return newRecent + kept + newOthers
}
