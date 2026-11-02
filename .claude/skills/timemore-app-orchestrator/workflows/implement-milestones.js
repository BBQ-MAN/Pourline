export const meta = {
  name: 'timemore-implement-milestones',
  description: '마일스톤마다 구현 → 빌드 검증 → QA·ECC 코드/디자인 리뷰 → 지적 교차 검증 → 수정 루프를 돈다',
  phases: [
    { title: '구현', detail: 'ble-engineer 또는 feature-engineer가 구현하고 verify.sh를 실행한다' },
    { title: '리뷰', detail: 'android-qa-inspector, ecc:kotlin-reviewer, UI 마일스톤은 ecc:a11y-architect와 시각 디테일 리뷰' },
    { title: '지적 검증', detail: 'low가 아닌 지적, 완료 조건 실패, 이전 라운드 미해결 지적을 검증자가 다시 판정한다' },
    { title: '수정', detail: '확정 지적, 완료 조건 실패, 빌드 실패를 담당 구현자가 고친다' },
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

// args: { milestones: Milestone[] (milestones.json 항목, 의존 순서대로), maxFixRounds?: number, reviewOnly?: boolean }
// reviewOnly: 이미 구현된 마일스톤을 다시 리뷰만 한다("QA 다시", "디자인 검증"). 확정 지적이 있으면 수정 라운드는 돈다.
const A = args || {}
const milestones = A.milestones || []
const maxFix = Number.isInteger(A.maxFixRounds) && A.maxFixRounds >= 0 ? A.maxFixRounds : 2
const reviewOnly = !!A.reviewOnly
if (!milestones.length) return { error: 'args.milestones가 비어 있다. milestones.json의 todo 항목을 넘겨야 한다.' }
const noAcc = milestones.filter(m => !(m.acceptance || []).length).map(m => m.id)
if (noAcc.length) return { error: `완료 조건(acceptance)이 없는 마일스톤: ${noAcc.join(', ')}. architect에게 판정 가능한 완료 조건을 받아야 한다.` }

const RESULT = {
  type: 'object', required: ['status', 'summary', 'filesChanged', 'verifyPassed', 'verifyExit'],
  properties: {
    status: { type: 'string', enum: ['done', 'failed'] },
    summary: { type: 'string' },
    filesChanged: { type: 'array', items: { type: 'string' } },
    verifyPassed: { type: 'boolean' },
    verifyExit: { type: 'integer', enum: [0, 1, 2] },
    verifyLog: { type: 'string' },
    screenshots: { type: 'array', items: { type: 'string' } },
    rejectedFindings: { type: 'array', items: { type: 'object', required: ['title', 'reason'], properties: {
      title: { type: 'string' }, reason: { type: 'string' } } } },
  },
}
const FINDING_ITEMS = { type: 'array', items: { type: 'object', required: ['title', 'severity', 'file', 'evidence', 'fix'], properties: {
  title: { type: 'string' }, severity: { type: 'string', enum: ['high', 'medium', 'low'] },
  file: { type: 'string' }, evidence: { type: 'string' }, fix: { type: 'string' } } } }
const FINDINGS = { type: 'object', required: ['findings'], properties: { findings: FINDING_ITEMS } }
// QA는 통과 판정의 근거이므로 빌드 결과와 완료 조건 판정을 반드시 내야 한다.
const QA_FINDINGS = {
  type: 'object', required: ['findings', 'verifyPassed', 'acceptance'],
  properties: {
    findings: FINDING_ITEMS,
    verifyPassed: { type: 'boolean' },
    acceptance: { type: 'array', minItems: 1, items: { type: 'object', required: ['criterion', 'result'], properties: {
      criterion: { type: 'string' }, result: { type: 'string', enum: ['pass', 'fail', 'unverified'] }, note: { type: 'string' } } } },
  },
}
const VERDICT = {
  type: 'object', required: ['status', 'reason'],
  properties: { status: { type: 'string', enum: ['confirmed', 'refuted', 'uncertain'] }, reason: { type: 'string' } },
}

// 도구가 제한된 에이전트도 스킬을 읽을 수 있게 파일 경로를 함께 준다.
const PS = n => `.claude/skills/${n}/SKILL.md`
const ECC = n => `ECC 스킬 ${n}(Skill 도구로 ecc:${n}를 불러온다. Skill 도구가 없으면 Glob으로 ~/.claude/plugins/cache/ecc/ecc/*/skills/${n}/SKILL.md 를 찾아 Read로 읽는다. 둘 다 안 되면 그 사실을 low 지적 하나로 보고한다)`
const SPECS = '명세: docs/spec/architecture.md, docs/spec/ui-design.md, docs/spec/protocol.md, docs/spec/protocol-vectors.json'
const VERIFY = '.claude/skills/android-build-verify/scripts/verify.sh'
const briefOf = m => `마일스톤 ${m.id} — ${m.title}\n범위: ${m.scope}\n완료 조건:\n${(m.acceptance || []).map(a => '- ' + a).join('\n')}`
const implOpts = (m, label, ph) => Object.assign({ agentType: m.owner, label, phase: ph, schema: RESULT }, m.model ? { model: m.model } : {})
const RESULT_RULE = `${VERIFY} 를 실행하고 종료 코드를 verifyExit에, 출력된 로그 경로(_workspace/build/verify_*.log)를 verifyLog에 적는다(verifyPassed는 종료 코드 0일 때만 true).`
const implPrompt = m => `${briefOf(m)}\n${SPECS}\n구현하고 테스트를 작성한 뒤 ${RESULT_RULE} 요약을 _workspace/03_${m.owner}_${m.id}_impl.md 에 남긴다.` +
  (m.ui ? `\n화면 마일스톤이다. ${PS('ui-design-review')} 3절대로 상태별 스크린샷을 만들고 경로를 screenshots에 모두 적는다.` : '')

function reviewersFor(m, changed, shots) {
  const scope = reviewOnly ? '리뷰 전용 실행이다. 이 마일스톤 범위의 현재 코드 전체를 검토한다.' : `변경 파일: ${changed.join(', ') || '(보고 없음)'}`
  const ctx = `${briefOf(m)}\n${scope}\n구현 요약: _workspace/03_${m.owner}_${m.id}_impl.md\n${SPECS}\n파일을 수정하지 말고 지적만 반환한다. 지적마다 파일:줄과 근거를 적는다.`
  const list = [
    { key: 'qa', agentType: 'android-qa-inspector', schema: QA_FINDINGS,
      prompt: `${PS('android-integration-qa')} 를 읽고 그 대조표로 통합 정합성을 검사하라. ${VERIFY} 를 직접 실행해 종료 코드 0이면 verifyPassed=true로 반환한다. 위 완료 조건 ${(m.acceptance || []).length}개 각각에 대해 criterion에 조건 문장을 그대로 옮기고 pass | fail | unverified 판정을 반환하라. 하나도 빠뜨리지 않는다.\n${ctx}` },
    { key: 'kotlin', agentType: 'ecc:kotlin-reviewer',
      prompt: `변경된 Kotlin 코드를 리뷰하라: 코루틴·Flow 안전성(취소, 디스패처, 수명주기), Compose 모범 사례(상태 읽기 범위, 재구성 비용, 안정성), Android 함정. ${PS('android-ble')} 와 ${PS('compose-app-dev')} 를 읽고 그 규칙을 기준에 포함한다. 프로젝트 스킬과 일반 관례가 충돌하면 프로젝트 스킬이 우선한다. 계층 간 계약 대조는 QA가 맡으므로 제외한다.\n${ctx}` },
  ]
  if (m.ui) {
    const s = shots.length ? shots.join(', ') : '(보고된 스크린샷 없음 — 이 자체를 high 지적으로 보고하라)'
    list.push(
      { key: 'a11y', agentType: 'ecc:a11y-architect',
        prompt: `${ECC('accessibility')} 를 기준으로, ${PS('ui-design-review')} 의 D3·D4·D5·D8·D9a·D9b 기준과 함께 WCAG 2.2 AA의 Android 적용을 검토하라. 스크린샷 PNG를 Read로 직접 열어 보고, Compose 코드의 semantics(contentDescription, stateDescription, liveRegion, role, onClickLabel, 터치 영역)를 함께 확인한다.\n스크린샷: ${s}\n${ctx}` },
      { key: 'visual',
        prompt: `${ECC('make-interfaces-feel-better')} 와 ${ECC('design-system')} 를 불러오고 ${PS('ui-design-review')} 를 따른다. 스크린샷 PNG를 Read로 직접 열어 보고 docs/spec/ui-design.md 의 토큰·상태 정의와 대조해 시각 디자인(D1·D2·D6·D7·D10·D11, 간격·위계·정렬·토큰 일관성)을 검토하라. 색 대비 수치가 필요하면 python3 -I .claude/skills/ui-design-review/scripts/contrast.py 로 계산한다.\n스크린샷: ${s}\n${ctx}` },
    )
  }
  return list
}

async function review(m, changed, shots, round) {
  const reviewers = reviewersFor(m, changed, shots)
  const run = (r, tag) => call(r.prompt,
    Object.assign({ label: `리뷰:${r.key}:${m.id}#${round}${tag}`, phase: '리뷰', schema: r.schema || FINDINGS }, r.agentType ? { agentType: r.agentType } : {}))
    .then(res => res && Object.assign({ key: r.key }, res))
  // QA가 완료 조건을 다 판정하지 않으면 결과가 없는 것과 같다.
  const usable = (r, x) => x && (r.key !== 'qa' || x.acceptance.length >= (m.acceptance || []).length)
  const pick = res => reviewers.map((r, i) => usable(r, res[i]) ? res[i] : null)
  let got = pick(await parallel(reviewers.map(r => () => run(r, ''))))
  const first = reviewers.filter((r, i) => !got[i])
  if (first.length) {
    log(`${m.id} 리뷰어 ${first.map(r => r.key).join(', ')} 결과 없음 또는 불완전 — 한 번 재시도`)
    const again = pick(await parallel(reviewers.map((r, i) => () => got[i] ? Promise.resolve(got[i]) : run(r, '#재시도'))))
    got = got.map((x, i) => x || again[i])
  }
  return { got: got.filter(Boolean), missing: reviewers.filter((r, i) => !got[i]).map(r => r.key) }
}

const NO_VERDICT = '검증 결과 없음'
const judge = (m, round, f, i) => call(
  `다음 리뷰 지적을 반박하려는 자세로 검증하라. 지적한 파일과 상대 코드(스크린샷이면 PNG)를 현재 상태로 직접 읽는다. 실제 오작동·접근성 위반·명세 위반·완료 조건 미충족을 일으키면 confirmed, 근거가 틀렸거나 이미 고쳐졌으면 refuted, 판단할 수 없으면 uncertain.` +
  (f.carried ? '\n이전 라운드에서 확정된 지적이다. 현재 코드에서 아직 유효한지 판정한다.' : '') +
  (f.rejection ? `\n구현자 반박: ${f.rejection}\n반박 근거가 맞으면 refuted, 틀리면 confirmed로 판정하고 이유를 적는다.` : '') +
  `\n지적: ${JSON.stringify(f)}\n${SPECS}`,
  { label: `검증:${m.id}#${round}.${i}`, phase: '지적 검증', schema: VERDICT, effort: 'high' })
const withVerdict = (f, v) => Object.assign({}, f, { verdict: v || { status: 'uncertain', reason: NO_VERDICT } })

const report = []
const failed = new Set()
// shortcut: 마일스톤을 순서대로 처리한다(Gradle 설정 같은 공유 파일 충돌 방지). 독립 마일스톤이 많아지면 dependsOn 기준 병렬화 + isolation: 'worktree'로 바꾼다.
for (const m of milestones) {
  if ((m.dependsOn || []).some(d => failed.has(d))) {
    failed.add(m.id)
    report.push({ id: m.id, status: 'skipped', reason: '선행 마일스톤 실패' })
    log(`${m.id} 건너뜀: 선행 마일스톤 실패`)
    continue
  }
  let impl
  if (reviewOnly) {
    impl = { status: 'done', summary: '리뷰 전용 실행', filesChanged: [], verifyPassed: true, verifyExit: 0, screenshots: m.screenshots || [] }
  } else {
    impl = await call(implPrompt(m), implOpts(m, `구현:${m.id}`, '구현'))
    if (!impl) {
      log(`${m.id} 구현 결과 없음 — 한 번 재시도`)
      impl = await call(implPrompt(m), implOpts(m, `구현:${m.id}#재시도`, '구현'))
    }
  }
  if (impl && impl.verifyExit === 2) {
    report.push({ id: m.id, status: 'env-missing', summary: impl.summary })
    log(`${m.id} 빌드 환경 없음(verify.sh 종료 코드 2) — 워크플로를 멈춘다`)
    return { error: 'ENV', milestone: m.id, report }
  }
  const changed = new Set(impl ? impl.filesChanged : [])
  let shots = (impl && impl.screenshots) || []
  let confirmed = [], uncertain = [], lows = [], acceptance = [], open = [], missing = []
  let qaOk = false, qaVerify = false, rounds = 0
  const rejected = []
  while (impl) {
    if (impl.verifyPassed) {
      const r = await review(m, [...changed], shots, rounds)
      missing = r.missing
      const qa = r.got.find(x => x.key === 'qa')
      acceptance = (qa && qa.acceptance) || []
      qaVerify = !!qa && qa.verifyPassed === true
      qaOk = qaVerify && acceptance.length >= m.acceptance.length
      const fresh = r.got.flatMap(x => (x.findings || []).map(f => Object.assign({}, f, { lens: x.key })))
      // 완료 조건 실패도 지적과 같은 경로(검증, 이월, 구현자 반박 재판정)로 다룬다.
      const accFails = acceptance.filter(a => a.result === 'fail').map(a => ({
        title: `완료 조건 실패: ${a.criterion}`, severity: 'high', file: '-', evidence: a.note || '', fix: '완료 조건을 만족하게 고친다', lens: 'acceptance' }))
      lows = fresh.filter(f => f.severity === 'low')   // low는 검증하지 않고 보고만 한다(비용)
      const freshJ = fresh.filter(f => f.severity !== 'low').concat(accFails)
      const rejectionOf = t => (rejected.find(x => x.title === t) || {}).reason
      const toJudge = freshJ
        .concat(open.filter(o => !freshJ.some(f => f.title === o.title)).map(o => Object.assign({}, o, { carried: true })))
        .map(f => Object.assign({}, f, { rejection: rejectionOf(f.title) }))
      const res = await parallel(toJudge.map((f, i) => () => judge(m, rounds, f, i)))
      const judged = toJudge.map((f, i) => withVerdict(f, res[i]))   // 검증자가 실패해도 지적은 남긴다
      confirmed = judged.filter(f => f.verdict.status === 'confirmed')
      uncertain = judged.filter(f => f.verdict.status === 'uncertain')
      // 검증자가 답하지 못한 지적은 보수적으로 막는다
      open = confirmed.concat(uncertain.filter(f => f.verdict.reason === NO_VERDICT))
    } else {
      qaOk = false   // 빌드가 깨졌으면 리뷰하지 않고 바로 수정한다. open은 다음 리뷰에서 다시 판정된다.
    }
    log(`${m.id} 라운드 ${rounds}: 빌드 ${impl.verifyPassed ? '통과' : '실패'}, QA ${qaOk ? '통과' : '미통과'}, 차단 지적 ${open.length}건${missing.length ? `, 리뷰어 누락 ${missing.join(',')}` : ''}`)
    if (missing.length) break   // 리뷰어가 없으면 판정할 수 없으므로 수정해도 소용없다
    if (impl.verifyPassed && impl.status === 'done' && qaOk && !open.length) break
    if (rounds >= maxFix) break
    rounds++
    const items = impl.verifyPassed ? open : []
    const fix = await call(
      `${briefOf(m)}\n${SPECS}\n아래를 고쳐라. 지적이 틀렸다고 판단하면 고치지 말고 rejectedFindings에 근거를 적는다. 이때 title에는 고칠 항목의 title을 글자 그대로 복사한다(검증자가 근거를 보고 다시 판정한다). 고친 뒤 ${RESULT_RULE}` +
      (m.ui ? ` 화면 마일스톤이므로 스크린샷을 갱신하고 현재 유효한 스크린샷 전체 목록을 screenshots에 적는다.` : '') +
      `\n빌드: ${impl.verifyPassed ? '통과' : '실패 — ' + (impl.verifyLog || '로그 경로 없음 — _workspace/build/ 의 최신 로그를 본다')}` +
      (impl.status === 'failed' ? `\n직전 구현자가 스스로 미완료로 보고했다: ${impl.summary}` : '') +
      (impl.verifyPassed && !qaVerify ? '\nQA가 verify.sh를 직접 실행했을 때 통과하지 않았다. _workspace/build/ 의 최신 로그를 확인한다.' : '') +
      (items.length ? `\n고칠 항목: ${JSON.stringify(items)}` : ''),
      implOpts(m, `수정:${m.id}#${rounds}`, '수정'))
    if (!fix) { log(`${m.id} 수정 결과 없음`); impl = null; break }
    if (fix.verifyExit === 2) {
      report.push({ id: m.id, status: 'env-missing', summary: fix.summary })
      return { error: 'ENV', milestone: m.id, report }
    }
    ;(fix.rejectedFindings || []).forEach(x => rejected.push(x))
    fix.filesChanged.forEach(f => changed.add(f))
    if (m.ui) shots = fix.screenshots || []   // 비어 있으면 다음 리뷰가 스크린샷 없음을 high로 지적한다
    impl = fix
  }
  const passed = !!impl && impl.verifyPassed && impl.status === 'done' && qaOk && !open.length && !missing.length
  const disputed = open.filter(f => rejected.some(x => x.title === f.title))
  const status = passed ? (acceptance.some(a => a.result === 'unverified') ? 'awaiting-device' : 'done')
    : (impl && !missing.length && disputed.length && disputed.length === open.length && impl.verifyPassed && qaOk) ? 'needs-decision'
    : 'failed'
  if (status === 'failed' || status === 'needs-decision') failed.add(m.id)
  report.push({
    id: m.id, status, rounds,
    verifyPassed: !!(impl && impl.verifyPassed), qaVerifyPassed: qaVerify,
    missingReviewers: missing,
    filesChanged: [...changed], screenshots: shots, acceptance,
    residual: open, uncertain, lowFindings: lows, rejected,
    summary: (impl && impl.summary) || '구현 에이전트 결과 없음',
  })
}
const count = s => report.filter(r => r.status === s).length
log(`완료 ${count('done')} / 실기기 대기 ${count('awaiting-device')} / 판단 필요 ${count('needs-decision')} / 실패 ${count('failed')} / 건너뜀 ${count('skipped')}`)
return { report }
