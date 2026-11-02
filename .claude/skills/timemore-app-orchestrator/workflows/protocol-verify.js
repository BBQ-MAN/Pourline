export const meta = {
  name: 'timemore-protocol-verify',
  description: 'Timemore 저울 프로토콜 명세·벡터를 작성하고, 작성자와 분리된 추출자가 뽑은 주장마다 원문·로그와 대조해 교차 검증한다',
  phases: [
    { title: '명세 작성', detail: 'scale-protocol-analyst가 원문으로 명세와 테스트 벡터를 만든다' },
    { title: '주장 추출', detail: '작성자가 아닌 에이전트가 명세 전체에서 검증할 주장을 빠짐없이 뽑는다' },
    { title: '주장 검증', detail: '주장마다 서로 다른 관점의 검증자 2명이 반박을 시도한다' },
    { title: '반영', detail: '검증 결과를 명세의 확정·미확정 표시에 반영하고 스크립트가 대조한다' },
  ],
}

// 세션 중간에 만든 .claude/agents/ 유형은 다음 세션부터 로드된다. 유형을 못 찾으면 정의 파일을 읽는 기본 에이전트로 대신 실행한다.
const call = (prompt, opts) => agent(prompt, opts).catch(e => {
  if (!opts || !opts.agentType || !/not found/i.test(String((e && e.message) || e))) throw e
  const rest = Object.assign({}, opts)
  delete rest.agentType
  log(`${opts.agentType} 유형을 찾지 못함 — .claude/agents/${opts.agentType}.md 를 읽는 기본 에이전트로 대신 실행`)
  return agent(`먼저 .claude/agents/${opts.agentType}.md 를 Read로 읽고, 그 문서의 역할·작업 원칙·입출력 규칙을 따르는 ${opts.agentType} 에이전트로서 아래 작업을 한다.\n\n${prompt}`, rest)
})

// args: { sourceDir?: string, feedback?: string, deviceLogs?: string[] }
const A = args || {}
const SOURCE = A.sourceDir || 'docs/protocol/source'
const SPEC = 'docs/spec/protocol.md'
const VECTORS = 'docs/spec/protocol-vectors.json'
const SKILL = '.claude/skills/scale-protocol/SKILL.md'
const logs = A.deviceLogs || []
const extra = [
  A.feedback ? `사용자 피드백: ${A.feedback}` : '',
  logs.length ? `실기기 로그(원문보다 우선하는 근거): ${logs.join(', ')}` : '',
].filter(Boolean).join('\n')

const CLAIMS = {
  type: 'object', required: ['claims'],
  properties: { claims: { type: 'array', items: { type: 'object', required: ['id', 'statement', 'sourceRef'], properties: {
    id: { type: 'string' }, statement: { type: 'string' }, sourceRef: { type: 'string' } } } } },
}
const VERDICT = {
  type: 'object', required: ['status', 'reason'],
  properties: { status: { type: 'string', enum: ['confirmed', 'refuted', 'uncertain'] }, reason: { type: 'string' } },
}
const MARKED = {
  type: 'object', required: ['marked', 'untracked'],
  properties: {
    marked: { type: 'array', items: { type: 'object', required: ['id', 'status'], properties: {
      id: { type: 'string' }, status: { type: 'string', enum: ['확정', '미확정'] } } } },
    untracked: { type: 'array', items: { type: 'string' } },
  },
}

phase('명세 작성')
const draft = await call(
  `${SKILL} 를 읽고 따른다.
원문: ${SOURCE}/ 아래 모든 파일
${extra}
${SPEC} 와 ${VECTORS} 를 작성하거나 갱신한다. 끝나면 바뀐 절을 한 문단으로 요약해 반환한다.`,
  { agentType: 'scale-protocol-analyst', phase: '명세 작성' })
if (!draft) return { error: '명세 작성 에이전트가 결과를 반환하지 않았다.' }

// 작성자가 주장을 고르면 뽑지 않은 내용이 검증 없이 확정처럼 남는다. 그래서 추출은 다른 에이전트가 한다.
phase('주장 추출')
const extracted = await call(
  `${SPEC} 와 ${VECTORS} 를 읽고 검증할 주장을 빠짐없이 뽑아라. 파일을 수정하지 않는다.
- "0. 확정 상태 요약" 표의 모든 행
- 프레임 오프셋 표의 모든 필드(오프셋, 길이, 형식, 부호, 엔디안, 단위·배율)
- 명령마다 opcode, cmd, payload 구조, 응답 형식
- CRC 파라미터와 계산 범위, CRC 바이트 순서
- GATT UUID와 쓰기 타입, 광고 식별 규칙
- 벡터마다 한 건: "벡터 <id>의 hex는 명세대로 디코딩하면 expect와 같다"
한 주장에는 사실 하나만 담는다. id는 고유해야 한다. 명세 표의 ID가 있으면 그 뒤에 필드를 붙이고(예: P-07.unit), 벡터는 벡터 id를 쓴다.
명세 표의 한 행이 주장 여러 개로 나뉘면 반영 단계는 그 주장이 모두 confirmed일 때만 행을 확정으로 둔다.
sourceRef에는 명세가 적은 근거 위치(원문 절, 로그 파일:줄)를 옮기고 근거가 없으면 "없음"으로 둔다.`,
  { label: '주장 추출', phase: '주장 추출', schema: CLAIMS })
if (!extracted || !extracted.claims.length) return { error: '추출된 주장이 0건이다. 명세가 비었거나 추출에 실패했다.', draft }
// 반영 대조가 id로 이뤄지므로 id가 겹치면 뒤쪽에 번호를 붙여 고유하게 만든다.
const seenIds = {}
const claims = extracted.claims.map(c => {
  seenIds[c.id] = (seenIds[c.id] || 0) + 1
  return seenIds[c.id] > 1 ? Object.assign({}, c, { id: `${c.id}#${seenIds[c.id]}` }) : c
})
const dupIds = Object.keys(seenIds).filter(k => seenIds[k] > 1)
log(`주장 ${claims.length}건 추출${dupIds.length ? ` (중복 id ${dupIds.length}개에 번호를 붙임: ${dupIds.join(', ')})` : ''}`)

phase('주장 검증')
const LENSES = [
  '원문 문서의 해당 절(sourceRef가 로그면 그 로그도)을 직접 읽고 문장 단위로 대조하는',
  '바이트 예시·로그 프레임·벡터로 값을 직접 재계산하는(CRC·오프셋·길이는 python3 -I 와 .claude/skills/scale-protocol/scripts/crc_probe.py 로 계산)',
]
const RULE = logs.length
  ? '실기기 로그가 주장을 뒷받침하면 원문과 달라도 confirmed로 판정하고 reason에 충돌을 적는다. 로그와 모순되면 refuted. 로그가 침묵하면 원문 기준으로 판정한다.'
  : '원문이 주장을 직접 뒷받침하면 confirmed, 모순되면 refuted.'
const judged = await parallel(claims.map(c => () =>
  parallel(LENSES.map((lens, i) => () => call(
    `다음 프로토콜 주장을 ${lens} 방식으로 검증하라. 반박하려는 자세로 본다. 파일을 수정하지 않는다.
주장: ${JSON.stringify(c)}
원문: ${SOURCE}/ , 명세: ${SPEC}, 벡터: ${VECTORS}
${extra}
${RULE} 근거가 침묵하거나 해석이 둘 이상이면 uncertain. 확신이 없으면 uncertain을 고른다.`,
    { label: `검증:${c.id}#${i}`, phase: '주장 검증', schema: VERDICT, effort: 'high' })))
    .then(vs => {
      const ok = vs.filter(Boolean)
      const status = ok.length === LENSES.length && ok.every(v => v.status === 'confirmed') ? 'confirmed'
        : ok.some(v => v.status === 'refuted') ? 'refuted' : 'unconfirmed'
      return Object.assign({}, c, { status, verdicts: ok })
    })))
const results = judged.filter(Boolean)
const partial = results.filter(r => r.verdicts.length < LENSES.length).length + (claims.length - results.length)
if (partial) log(`검증자 결과가 빠진 주장 ${partial}건 — 확정되지 않는다`)
const by = s => results.filter(r => r.status === s)
log(`확정 ${by('confirmed').length} / 미확정 ${by('unconfirmed').length} / 반박 ${by('refuted').length}`)

phase('반영')
const applied = await call(
  `${SKILL} 를 따른다. 검증 결과를 ${SPEC} 와 ${VECTORS} 에 반영하라.
- confirmed: 확정
- unconfirmed: 미확정 + 실기기 확인 방법(device-verification 스킬 프로브 A의 해당 단계)
- refuted: 검증 근거로 명세를 고치고, 고친 항목은 "미확정(수정됨, 재검증 필요)"으로 둔다. 다음 실행에서 다시 검증된다
- 아래 결과 목록에 없는데 명세에 확정으로 표시된 항목은 미확정으로 바꾸고 untracked에 적는다
반환: 결과 목록의 id마다 반영 후 상태(marked), 그리고 untracked.
결과: ${JSON.stringify(results.map(r => ({ id: r.id, statement: r.statement, status: r.status, reasons: r.verdicts.map(v => v.reason) })))}`,
  { agentType: 'scale-protocol-analyst', phase: '반영', schema: MARKED })
if (!applied) return { error: '반영 에이전트가 결과를 반환하지 않았다. 명세의 확정 표시를 믿지 말고 다시 실행한다.', results }

const markOf = id => (applied.marked.find(x => x.id === id) || {}).status
const mismatches = results.filter(r => (r.status === 'confirmed') !== (markOf(r.id) === '확정'))
  .map(r => ({ id: r.id, verdict: r.status, marked: markOf(r.id) || '(없음)' }))
if (mismatches.length) log(`반영 불일치 ${mismatches.length}건 — 반환값 mismatches 확인`)

return {
  specPath: SPEC, vectorsPath: VECTORS,
  confirmed: by('confirmed'), unconfirmed: by('unconfirmed'), refuted: by('refuted'),
  partialVerdicts: partial, mismatches, untracked: applied.untracked, draftSummary: draft,
}
