// implement-milestones.js 판정 로직 회귀 테스트. 가짜 agent()로 시나리오별 최종 status를 확인한다.
// 실행: node .claude/skills/timemore-app-orchestrator/workflows/implement-milestones.test.cjs
const fs = require('fs')
const path = require('path')
const src = fs.readFileSync(path.join(__dirname, 'implement-milestones.js'), 'utf8').replace(/^export const meta/m, 'const meta')
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor
const wf = new AsyncFunction('args', 'agent', 'parallel', 'pipeline', 'phase', 'log', src)
const parallel = async ts => Promise.all(ts.map(t => t().catch(() => null)))   // 런타임처럼 throw를 null로

const M = { id: 'M1', title: 't', owner: 'feature-engineer', scope: 's', acceptance: ['a1', 'a2'], ui: false }
const ok = { status: 'done', summary: 'ok', filesChanged: ['x.kt'], verifyPassed: true, verifyExit: 0 }
const acc = (r1, r2) => [{ criterion: 'a1', result: r1 }, { criterion: 'a2', result: r2 }]
const qaPass = { findings: [], verifyPassed: true, acceptance: acc('pass', 'pass') }
const H = (t, severity = 'high') => ({ title: t, severity, file: 'f', evidence: 'e', fix: 'x' })

async function run(script) {
  const n = { fix: 0 }
  const agent = async (p, o) => {
    const l = o.label
    if (l.startsWith('구현')) return script.impl ? script.impl(n) : ok
    if (l.startsWith('수정')) { n.fix++; return script.fix ? script.fix(n) : ok }
    if (l.startsWith('리뷰:qa')) return script.qa ? script.qa(n) : qaPass
    if (l.startsWith('리뷰:kotlin')) return script.kotlin ? script.kotlin(n) : { findings: [] }
    if (l.startsWith('검증')) return script.judge ? script.judge(n) : { status: 'confirmed', reason: 'r' }
    throw new Error('unexpected ' + l)
  }
  const r = await wf({ milestones: [M], maxFixRounds: 2 }, agent, parallel, null, () => {}, () => {})
  return r.report ? r.report[0].status : 'ERR:' + r.error
}

const cases = [
  ['깨끗한 통과', {}, 'done'],
  ['QA 두 번 결과 없음', { qa: () => null }, 'failed'],
  ['QA가 완료 조건 일부만 판정', { qa: () => ({ findings: [], verifyPassed: true, acceptance: [{ criterion: 'a1', result: 'pass' }] }) }, 'failed'],
  ['완료 조건 unverified', { qa: () => ({ findings: [], verifyPassed: true, acceptance: acc('pass', 'unverified') }) }, 'awaiting-device'],
  ['high 지적 수정 후 통과', { kotlin: n => ({ findings: n.fix ? [] : [H('X')] }), judge: n => ({ status: n.fix ? 'refuted' : 'confirmed', reason: 'r' }) }, 'done'],
  ['구현자 거부, 검증자 유지', { kotlin: () => ({ findings: [H('X')] }), fix: () => Object.assign({}, ok, { rejectedFindings: [{ title: 'X', reason: 'no' }] }) }, 'needs-decision'],
  ['확정 지적을 low로 낮춰 보고', { kotlin: n => ({ findings: [H('X', n.fix ? 'low' : 'high')] }) }, 'failed'],
  ['완료 조건 fail', { qa: () => ({ findings: [], verifyPassed: true, acceptance: acc('fail', 'pass') }) }, 'failed'],
  ['검증자 throw는 차단', { kotlin: () => ({ findings: [H('X')] }), judge: () => { throw new Error('budget') } }, 'failed'],
  ['빌드 실패 후 수정', { impl: () => Object.assign({}, ok, { verifyPassed: false, verifyExit: 1 }) }, 'done'],
  ['환경 없음', { impl: () => Object.assign({}, ok, { verifyPassed: false, verifyExit: 2 }) }, 'env-missing'],
]

;(async () => {
  let bad = 0
  for (const [name, script, expect] of cases) {
    const got = await run(script)
    if (got !== expect) bad++
    console.log(`${got === expect ? 'PASS' : 'FAIL'} ${name}: ${got}${got === expect ? '' : ` (기대 ${expect})`}`)
  }
  process.exit(bad ? 1 : 0)
})()
