export const meta = {
  name: 'timemore-design-review',
  description: 'ECC 리뷰어로 아키텍처·UI 디자인 명세를 검토하고, 지적을 교차 검증한 뒤 담당 설계자가 반영하고 문서 정합성을 다시 확인한다',
  phases: [
    { title: '설계 리뷰', detail: 'ecc:architect, ecc:a11y-architect, 시각 디테일(ECC 스킬), 문서 정합성' },
    { title: '지적 검증', detail: '지적마다 검증자가 반박을 시도한다' },
    { title: '반영', detail: 'android-architect와 ui-designer가 확정 지적을 반영한다' },
    { title: '재확인', detail: '반영 뒤 문서끼리 다시 맞는지 한 번 확인한다' },
  ],
}

// 모델 라우팅(2026-11-05): agentType 없는 추출·검증·리뷰 호출은 sonnet 으로 고정한다. 지정하지 않으면 세션 모델(opus)을 상속해 주장·지적마다 opus 가 돈다.
// 세션 중간에 만든 .claude/agents/ 유형은 다음 세션부터 로드된다. 유형을 못 찾으면 정의 파일을 읽는 기본 에이전트로 대신 실행한다.
const call = (prompt, opts) => agent(prompt, opts).catch(e => {
  if (!opts || !opts.agentType || !/not found/i.test(String((e && e.message) || e))) throw e
  const rest = Object.assign({}, opts)
  delete rest.agentType
  log(`${opts.agentType} 유형을 찾지 못함 — .claude/agents/${opts.agentType}.md 를 읽는 기본 에이전트로 대신 실행`)
  return agent(`먼저 .claude/agents/${opts.agentType}.md 를 Read로 읽고, 그 문서의 역할·작업 원칙·입출력 규칙을 따르는 ${opts.agentType} 에이전트로서 아래 작업을 한다.\n\n${prompt}`, rest)
})

// args: { only?: string[] }  — 일부 관점만 다시 돌릴 때(예: ['a11y', 'visual'])
const A = args || {}
const TARGETS = ['architecture', 'ui-design', 'protocol']
const FINDINGS = {
  type: 'object', required: ['findings'],
  properties: { findings: { type: 'array', items: { type: 'object',
    required: ['title', 'severity', 'targets', 'location', 'evidence', 'fix'],
    properties: {
      title: { type: 'string' },
      severity: { type: 'string', enum: ['high', 'medium', 'low'] },
      targets: { type: 'array', minItems: 1, items: { type: 'string', enum: TARGETS } },
      location: { type: 'string' }, evidence: { type: 'string' }, fix: { type: 'string' },
    } } } },
}
const VERDICT = {
  type: 'object', required: ['status', 'reason'],
  properties: { status: { type: 'string', enum: ['confirmed', 'refuted', 'uncertain'] }, reason: { type: 'string' } },
}
const APPLIED = {
  type: 'object', required: ['applied', 'declined'],
  properties: {
    applied: { type: 'array', items: { type: 'string' } },
    declined: { type: 'array', items: { type: 'object', required: ['title', 'reason'], properties: {
      title: { type: 'string' }, reason: { type: 'string' } } } },
  },
}
const PS = n => `.claude/skills/${n}/SKILL.md`
const ECC = n => `ECC 스킬 ${n}(Skill 도구로 ecc:${n}를 불러온다. Skill 도구가 없으면 Glob으로 ~/.claude/plugins/cache/ecc/ecc/*/skills/${n}/SKILL.md 를 찾아 Read로 읽는다. 둘 다 안 되면 그 사실을 low 지적 하나로 보고한다)`
const DOCS = '설계 문서: docs/spec/architecture.md, docs/spec/milestones.json, docs/spec/ui-design.md, docs/spec/protocol.md'
const READ_ONLY = '파일을 수정하지 말고 지적만 반환한다. 지적마다 문서와 절(location), 근거, 고칠 방법을 적고 고쳐야 할 문서를 targets(architecture | ui-design | protocol, 여럿 가능)로 고른다. 문서 사이 불일치면 양쪽을 넣고 fix에 어느 쪽을 기준으로 맞출지 적는다.'
const CONSISTENCY = `${PS('android-integration-qa')} 의 설계 단계 방식으로 문서끼리 대조한다: ui-design.md 화면 상태 ↔ architecture.md 상태 모델, 화면 표시 값 ↔ protocol.md 가 제공하는 값의 범위·해상도·단위, milestones.json 이 모든 화면과 계약을 덮는지, 화면을 바꾸는 마일스톤에 ui: true 가 있는지.`
const REVIEWERS = [
  { key: 'architecture', agentType: 'ecc:architect',
    prompt: `${ECC('android-clean-architecture')} 와 ${ECC('kotlin-coroutines-flows')} 를 참고하되, ${PS('compose-app-dev')} 와 ${PS('android-ble')} 를 읽고 그 규칙을 우선한다. 프로젝트 스킬과 ECC 패턴이 충돌하면 프로젝트 스킬을 따르고, ECC 패턴을 쓰지 않았다는 사실만으로는 지적하지 않는다. architecture.md 와 milestones.json 의 실제 결함을 찾는다: 계층 의존 역전, 인터페이스 계약의 단위·스레드·수명주기 누락, 연결 소유 범위, 테스트 불가 구조, 미확정 프로토콜 항목의 격리, 마일스톤 순서와 완료 조건의 판정 가능성.` },
  { key: 'a11y', agentType: 'ecc:a11y-architect',
    prompt: `${ECC('accessibility')} 를 기준으로, ${PS('ui-design-review')} 의 D3·D4·D5·D8·D9a·D9b 기준과 함께 WCAG 2.2 AA의 Android 적용 관점에서 ui-design.md 를 검토한다. 색 대비는 ui-design.md 의 대비 표 수치를 확인하고, 표가 없거나 수치가 빠졌으면 지적한다.` },
  { key: 'visual',
    prompt: `${ECC('make-interfaces-feel-better')} 와 ${ECC('design-system')} 를 불러오고 ${PS('ui-design-review')} 를 따른다. ui-design.md 의 토큰 체계, 타이포 위계, 숫자 표시(D1·D2), 애니메이션·전환 규칙(D6), 간격, 화면 상태 정의(D10·D11), 그래프(D7), 추출 중 가독성을 검토한다. 대비 수치는 python3 -I .claude/skills/ui-design-review/scripts/contrast.py 로 직접 계산한다.` },
  { key: 'consistency', agentType: 'android-qa-inspector', prompt: CONSISTENCY },
]
const OWNERS = { architecture: 'android-architect', 'ui-design': 'ui-designer' }
const reviewers = A.only ? REVIEWERS.filter(r => A.only.includes(r.key)) : REVIEWERS
if (!reviewers.length) return { error: `args.only가 알 수 없는 관점만 담고 있다: ${A.only}` }

const NO_VERDICT = '검증 결과 없음'
const runReview = (r, tag) => call(`${r.prompt}\n${DOCS}\n${READ_ONLY}`,
  Object.assign({ label: `리뷰:${r.key}${tag}`, phase: '설계 리뷰', schema: FINDINGS }, r.agentType ? { agentType: r.agentType } : { model: 'sonnet' }))
const reviewed = await pipeline(
  reviewers,
  r => runReview(r, '').then(res => res || (log(`리뷰어 ${r.key} 결과 없음 — 한 번 재시도`), runReview(r, '#재시도')))
    .then(res => res || { failed: true }),
  (res, r) => {
    if (res.failed) return { failed: true }
    return parallel(res.findings.map((f, i) => () => call(
      `다음 설계 지적을 반박하려는 자세로 검증하라. 해당 문서 절과 관련 명세를 직접 읽는다. 파일을 수정하지 않는다. 대비 수치는 python3 -I .claude/skills/ui-design-review/scripts/contrast.py 로 직접 계산한다. 지적대로 두면 구현에서 실제 결함(오작동, 접근성 위반, 계약 불일치)이 생기면 confirmed, 근거가 틀렸거나 이미 문서에 반영돼 있으면 refuted, 판단할 수 없으면 uncertain.\n지적: ${JSON.stringify(f)}\n${DOCS}`,
      { label: `검증:${r.key}.${i}`, phase: '지적 검증', schema: VERDICT, effort: 'high', model: 'sonnet' })))
      // 검증자가 실패(null 또는 throw)해도 지적은 판단 보류로 남긴다
      .then(vs => res.findings.map((f, i) => Object.assign({}, f, { lens: r.key, verdict: vs[i] || { status: 'uncertain', reason: NO_VERDICT } })))
  })
// pipeline 단계가 throw하면 null이 되므로, 결과가 배열이 아닌 리뷰어는 모두 실패로 센다.
const failedReviewers = reviewers.filter((r, i) => !Array.isArray(reviewed[i])).map(r => r.key)
if (failedReviewers.length) log(`리뷰 실패: ${failedReviewers.join(', ')}`)
const all = reviewed.filter(Array.isArray).flat()
const confirmed = all.filter(f => f.verdict.status === 'confirmed')
const uncertain = all.filter(f => f.verdict.status === 'uncertain')
log(`지적 ${all.length}건 중 확정 ${confirmed.length}, 판단 보류 ${uncertain.length}(검증 결과 없음 ${uncertain.filter(f => f.verdict.reason === NO_VERDICT).length}), 기각 ${all.length - confirmed.length - uncertain.length}`)

// 장벽: 담당 문서별로 확정 지적을 모아 한 번에 반영해야 같은 절을 여러 번 고치지 않는다.
phase('반영')
const revisions = await parallel(Object.entries(OWNERS).map(([target, owner]) => () => {
  const items = confirmed.filter(f => f.targets.includes(target))
  if (!items.length) return Promise.resolve({ target, owner, applied: [], declined: [] })
  const files = target === 'architecture' ? 'docs/spec/architecture.md, docs/spec/milestones.json' : 'docs/spec/ui-design.md'
  return call(`설계 검증에서 확정된 지적을 ${files} 에 반영하라. 지적된 절만 고친다. 다른 문서와의 불일치 지적이면 fix가 정한 기준 쪽에 맞춘다. 반영한 지적 제목은 applied에, 반영하지 않은 지적은 이유와 함께 declined에 적는다.\n지적: ${JSON.stringify(items)}`,
    { agentType: owner, label: `반영:${owner}`, phase: '반영', schema: APPLIED })
    .then(r => {
      if (!r) return { target, owner, failed: true, items: items.map(f => f.title) }
      // applied에도 declined에도 없는 확정 지적은 반영 여부를 알 수 없으므로 따로 보고한다
      const unapplied = items.filter(f => !r.applied.includes(f.title) && !r.declined.some(d => d.title === f.title)).map(f => f.title)
      return Object.assign({ target, owner, unapplied }, r)
    })
}))
const revs = Object.keys(OWNERS).map((target, i) => revisions[i] || { target, owner: OWNERS[target], failed: true, items: [] })
const failedRevisions = revs.filter(r => r.failed)
if (failedRevisions.length) log(`반영 실패: ${failedRevisions.map(r => r.owner).join(', ')}`)

// 한 문서라도 바뀌었으면 다른 문서와 어긋났을 수 있으므로 정합성만 한 번 더 본다.
phase('재확인')
let postRevision = [], postRevisionFailed = false
const changedDocs = revs.filter(r => r.applied && r.applied.length).map(r => r.target)
if (changedDocs.length) {
  const again = await call(`${CONSISTENCY}\n방금 수정된 문서: ${changedDocs.join(', ')}. 이 수정으로 새로 생긴 불일치만 찾는다.\n${DOCS}\n${READ_ONLY}`,
    { agentType: 'android-qa-inspector', label: '재확인:consistency', phase: '재확인', schema: FINDINGS })
  if (!again) { postRevisionFailed = true; log('재확인 결과 없음') }
  postRevision = (again && again.findings) || []
}

return {
  confirmed,
  uncertain,
  failedReviewers,
  protocolIssues: confirmed.filter(f => f.targets.includes('protocol')),
  applied: revs.flatMap(r => (r.applied || []).map(t => ({ owner: r.owner, title: t }))),
  declined: revs.flatMap(r => (r.declined || []).map(d => Object.assign({ owner: r.owner }, d))),
  unapplied: revs.flatMap(r => (r.unapplied || []).map(t => ({ owner: r.owner, title: t }))),
  failedRevisions,
  postRevision,
  postRevisionFailed,
}
