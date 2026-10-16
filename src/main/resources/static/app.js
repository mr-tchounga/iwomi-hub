/* IWOMI Hub - single-page UI (vanilla JS, no build step): staff app + public portal */
const $ = (s, el = document) => el.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtDate = d => d ? new Date(d).toLocaleDateString() : '—';
const fmtTime = d => d ? new Date(d).toLocaleString() : '—';
const money = (n, cur = 'XAF') => n == null ? '—' : new Intl.NumberFormat('fr-FR').format(n) + ' ' + cur;

let TOKEN = localStorage.getItem('hub.token') || '';
let USER = null;

function logout() {
  localStorage.removeItem('hub.token');
  location.hash = '';
  location.reload();
}

async function api(path, method = 'GET', body) {
  const headers = {};
  if (body) headers['Content-Type'] = 'application/json';
  if (TOKEN) headers['Authorization'] = 'Bearer ' + TOKEN;
  const r = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  if (r.status === 401 && TOKEN && !path.startsWith('/api/auth/login')) { logout(); throw new Error('Session expired, please log in again'); }
  if (!r.ok) {
    let m = r.statusText;
    try { m = (await r.json()).error || m; } catch (e) { /* no json body */ }
    throw new Error(m);
  }
  if (r.status === 204) return null;
  return r.json().catch(() => null);
}

function toast(msg, err) {
  const t = document.createElement('div');
  t.className = 'toast' + (err ? ' err' : '');
  t.textContent = msg;
  $('#toasts').appendChild(t);
  setTimeout(() => t.remove(), 5000);
}

/* ---------- roles and permissions (the server enforces the same rules) ---------- */
const STAFF_ROLES = ['ADMIN', 'CTO', 'HR', 'ACCOUNTANT', 'EMPLOYEE'];
const ROLE_LABEL = { ADMIN: 'Administrator', CTO: 'CTO', HR: 'HR (RH)', ACCOUNTANT: 'Accountant', EMPLOYEE: 'Employee', EXTERNAL: 'Customer' };
const PERM = {
  projects: { r: STAFF_ROLES, w: ['ADMIN', 'CTO'] },
  partners: { r: STAFF_ROLES, w: ['ADMIN', 'CTO', 'ACCOUNTANT'] },
  sales: { r: ['ADMIN', 'CTO', 'ACCOUNTANT'], w: ['ADMIN', 'ACCOUNTANT'] },
  kyc: { r: ['ADMIN', 'CTO', 'ACCOUNTANT'], w: ['ADMIN', 'ACCOUNTANT'] },
  team: { r: STAFF_ROLES, w: ['ADMIN', 'HR'] },
  tickets: { r: ['ADMIN', 'CTO', 'EMPLOYEE'], w: ['ADMIN', 'CTO', 'EMPLOYEE'] },
  tasks: { r: ['ADMIN', 'CTO', 'HR', 'EMPLOYEE'], w: ['ADMIN', 'CTO'] },
  accelerator: { r: ['ADMIN', 'CTO', 'EMPLOYEE'], w: ['ADMIN', 'CTO', 'EMPLOYEE'] },
  settings: { r: ['ADMIN', 'CTO'], w: ['ADMIN', 'CTO'] },
  users: { r: ['ADMIN'], w: ['ADMIN'] },
};
const can = (area, mode = 'r') => !!USER && PERM[area][mode].includes(USER.role);

/* ---------- data ---------- */
let D = {};
let N = [];
const nameOf = (list, id, key = 'name') => { const x = list.find(i => i.id === id); return x ? x[key] : '—'; };
const empName = id => id ? nameOf(D.emps, id, 'fullName') : '—';
const opts = (list, key = 'name') => list.map(i => ({ v: i.id, l: i[key] }));
const enumOpts = arr => arr.map(v => ({ v, l: v.replace(/_/g, ' ') }));
const badge = t => t ? `<span class="badge ${esc(String(t).toLowerCase().replace(/_/g, '-'))}">${esc(String(t).replace(/_/g, ' '))}</span>` : '';
const find = (list, id) => list.find(x => x.id === id);
const toLong = v => v === null || v === undefined || v === '' ? null : Number(v);

const COMP_TYPES = ['API', 'FRONTOFFICE', 'BACKOFFICE', 'MOBILE'];
const TICKET_STATUS = ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
const PRIORITY = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
const TASK_STATUS = ['PLANNED', 'IN_PROGRESS', 'BLOCKED', 'DONE', 'CANCELLED'];
const ROLES = ['ADMIN', 'CTO', 'HR', 'ACCOUNTANT', 'EMPLOYEE', 'EXTERNAL'];

async function loadData() {
  const maybe = (area, path) => can(area) ? api(path) : Promise.resolve([]);
  const r = await Promise.all([api('/api/departments'), api('/api/employees'), api('/api/projects'), api('/api/components'),
    api('/api/partners'), maybe('sales', '/api/sales'), maybe('kyc', '/api/kyc')]);
  D = { depts: r[0], emps: r[1], projects: r[2], components: r[3], partners: r[4], sales: r[5], kycs: r[6] };
}

/* ---------- ui helpers ---------- */
const head = (title, right) => `<div class="head"><h2>${title}</h2><div class="row">${right || ''}</div></div>`;
const btn = (act, id, label, cls = '', ctx = '') => `<button class="sm ${cls}" data-act="${act}" data-id="${id ?? ''}" data-ctx="${esc(ctx)}">${label}</button>`;

function table(cols, rows, actions) {
  const th = cols.map(c => `<th>${esc(c.h)}</th>`).join('') + (actions ? '<th></th>' : '');
  const body = rows.map(r => `<tr>${cols.map(c => `<td>${c.f(r)}</td>`).join('')}${actions ? `<td class="act">${actions(r)}</td>` : ''}</tr>`).join('')
    || `<tr><td colspan="${cols.length + 1}" class="muted">Nothing here yet</td></tr>`;
  return `<table><thead><tr>${th}</tr></thead><tbody>${body}</tbody></table>`;
}

function fieldHtml(f, v) {
  const req = f.required ? 'required' : '';
  let input;
  if (f.type === 'textarea') input = `<textarea name="${f.name}" ${req}>${esc(v)}</textarea>`;
  else if (f.type === 'select') {
    input = `<select name="${f.name}" ${req}>${f.required ? '' : '<option value="">—</option>'}${f.options.map(o => `<option value="${esc(o.v)}" ${String(o.v) === String(v ?? '') ? 'selected' : ''}>${esc(o.l)}</option>`).join('')}</select>`;
  } else if (f.type === 'multi') {
    input = f.options.map(o => `<label class="chk"><input type="checkbox" name="${f.name}" value="${esc(o.v)}" ${(v || []).includes(o.v) ? 'checked' : ''}> ${esc(o.l)}</label>`).join('');
    return `<div class="fld">${esc(f.label)}<div>${input}</div></div>`;
  } else input = `<input name="${f.name}" type="${f.type || 'text'}" ${f.type === 'number' ? 'step="any"' : ''} value="${esc(v)}" ${req}>`;
  return `<label>${esc(f.label)}${input}</label>`;
}

function openForm(title, fields, values, onSave) {
  const m = $('#modal');
  m.innerHTML = `<div class="box"><h3 style="margin-top:0">${esc(title)}</h3><form>${fields.map(f => fieldHtml(f, (values || {})[f.name])).join('')}<div class="actions"><button type="button" class="ghost" id="cancel">Cancel</button><button class="primary">Save</button></div></form></div>`;
  m.classList.add('open');
  $('#cancel', m).onclick = () => m.classList.remove('open');
  $('form', m).onsubmit = async e => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const out = {};
    for (const f of fields) {
      if (f.type === 'multi') out[f.name] = fd.getAll(f.name).map(Number);
      else {
        let v = fd.get(f.name);
        if (v === '' || v === null) v = null;
        else if (f.type === 'number') v = Number(v);
        out[f.name] = v;
      }
    }
    try { await onSave(out); m.classList.remove('open'); await refresh(); } catch (err) { toast(err.message, true); }
  };
}

function showInfo(title, html) {
  const m = $('#modal');
  m.innerHTML = `<div class="box"><h3 style="margin-top:0">${esc(title)}</h3>${html}<div class="actions"><button class="primary" id="closeInfo">Close</button></div></div>`;
  m.classList.add('open');
  $('#closeInfo', m).onclick = () => m.classList.remove('open');
}

const save = (path, existing, out) => existing && existing.id ? api(`${path}/${existing.id}`, 'PUT', { ...existing, ...out }) : api(path, 'POST', out);

async function del(path) {
  if (!confirm('Delete this item?')) return;
  try { await api(path, 'DELETE'); await refresh(); } catch (e) { toast(e.message, true); }
}
async function refresh() { if (isStaff()) await loadData(); await route(); }
const isStaff = () => !!USER && USER.role !== 'EXTERNAL';

/* tiny markdown renderer for AI-generated documentation */
function md(src) {
  const parts = String(src || '').split('```');
  return '<div class="md">' + parts.map((b, i) => i % 2 ? `<pre><code>${esc(b.replace(/^[\w-]*\n/, ''))}</code></pre>` : esc(b).split('\n').map(line => {
    if (/^#{1,2} /.test(line)) return `<h3>${line.replace(/^#+ /, '')}</h3>`;
    if (/^#{3,} /.test(line)) return `<h4>${line.replace(/^#+ /, '')}</h4>`;
    if (/^[-*] /.test(line)) return `<li>${line.slice(2)}</li>`;
    return line.trim() ? `<p>${line}</p>` : '';
  }).join('').replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/`([^`]+)`/g, '<code>$1</code>')).join('') + '</div>';
}
function download(name, text) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([text], { type: 'text/markdown' }));
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}

/* ---------- forms (staff) ---------- */
function projectForm(p) {
  openForm(p ? 'Edit project' : 'New project', [
    { name: 'name', label: 'Name', required: true }, { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'status', label: 'Status', type: 'select', required: true, options: enumOpts(['ACTIVE', 'MAINTENANCE', 'ARCHIVED']) },
    { name: 'publicVisible', label: 'Shown on the public portal', type: 'select', required: true, options: [{ v: 'true', l: 'Yes' }, { v: 'false', l: 'No (internal only)' }] }],
    p ? { ...p, publicVisible: String(p.publicVisible) } : { status: 'ACTIVE', publicVisible: 'true' },
    out => save('/api/projects', p, { ...out, publicVisible: out.publicVisible === 'true' }));
}
function componentForm(projectId, c) {
  openForm(c ? 'Edit component' : 'Add component', [
    { name: 'type', label: 'Type', type: 'select', required: true, options: enumOpts(COMP_TYPES) },
    { name: 'name', label: 'Name' }, { name: 'repoUrl', label: 'Repository URL' }, { name: 'stack', label: 'Tech stack' },
    { name: 'version', label: 'Version' }, { name: 'environmentUrls', label: 'Environments (one per line)', type: 'textarea' }],
    c || {}, out => save('/api/components', c, { ...out, projectId }));
}
function saleForm(fixed, s) {
  const fields = [];
  if (fixed.projectId) fields.push({ name: 'partnerId', label: 'Partner', type: 'select', required: true, options: opts(D.partners) });
  else fields.push({ name: 'projectId', label: 'Project', type: 'select', required: true, options: opts(D.projects) });
  fields.push({ name: 'soldAt', label: 'Sold on', type: 'date' }, { name: 'amount', label: 'Amount', type: 'number' },
    { name: 'currency', label: 'Currency' },
    { name: 'status', label: 'Status', type: 'select', required: true, options: enumOpts(['ACTIVE', 'ENDED']) });
  openForm(s ? 'Edit sale' : 'Sell to partner', fields, s || { status: 'ACTIVE', currency: 'XAF' },
    out => save('/api/sales', s, { ...out, ...fixed, currency: out.currency || 'XAF', partnerId: toLong(out.partnerId ?? fixed.partnerId), projectId: toLong(out.projectId ?? fixed.projectId) }));
}
function partnerForm(p) {
  openForm(p ? 'Edit partner' : 'New partner', [
    { name: 'name', label: 'Name', required: true }, { name: 'contactEmail', label: 'Contact email', type: 'email' }, { name: 'country', label: 'Country' }],
    p || { country: 'Cameroon' }, out => save('/api/partners', p, out));
}
function kycForm(partnerId, k) {
  openForm('Partner KYC', [
    { name: 'legalName', label: 'Legal name', required: true }, { name: 'registrationNumber', label: 'Registration number' },
    { name: 'address', label: 'Address' }, { name: 'representativeName', label: 'Representative' }, { name: 'notes', label: 'Notes', type: 'textarea' }],
    k || {}, out => save('/api/kyc', k, { ...out, partnerId }));
}
function docForm(kycId) {
  openForm('Add KYC document', [{ name: 'docType', label: 'Document type', required: true }, { name: 'fileName', label: 'File name' }, { name: 'url', label: 'Link (URL)' }],
    {}, out => api('/api/kyc-documents', 'POST', { ...out, kycId }));
}
function ticketForm() {
  openForm('New ticket', [
    { name: 'subject', label: 'Subject', required: true }, { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'category', label: 'Category (e.g. BUG, PAYMENT, ACCOUNTING, ONBOARDING)' },
    { name: 'priority', label: 'Priority', type: 'select', required: true, options: enumOpts(PRIORITY) },
    { name: 'projectId', label: 'Project', type: 'select', options: opts(D.projects) },
    { name: 'partnerId', label: 'Partner', type: 'select', options: opts(D.partners) },
    { name: 'reporterName', label: 'Reporter name' }, { name: 'reporterContact', label: 'Reporter contact' }],
    { priority: 'MEDIUM' }, out => api('/api/tickets', 'POST', { ...out, projectId: toLong(out.projectId), partnerId: toLong(out.partnerId), source: 'STAFF' }));
}
function taskForm(t) {
  openForm(t ? 'Edit task' : 'New task', [
    { name: 'title', label: 'Title', required: true }, { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'projectId', label: 'Project', type: 'select', options: opts(D.projects) },
    { name: 'assigneeId', label: 'Assignee', type: 'select', options: opts(D.emps, 'fullName') },
    { name: 'status', label: 'Status', type: 'select', required: true, options: enumOpts(TASK_STATUS) },
    { name: 'startDate', label: 'Start date', type: 'date' }, { name: 'dueDate', label: 'Due date', type: 'date' }],
    t || { status: 'PLANNED' }, out => save('/api/tasks', t, { ...out, projectId: toLong(out.projectId), assigneeId: toLong(out.assigneeId) }));
}
function statusForm(t) {
  openForm('Update my task: ' + t.title, [{ name: 'status', label: 'Status', type: 'select', required: true, options: enumOpts(TASK_STATUS) }],
    t, out => api('/api/tasks/' + t.id, 'PUT', { ...t, status: out.status }));
}
function deptForm(d) {
  openForm(d ? 'Edit department' : 'New department', [
    { name: 'name', label: 'Name', required: true }, { name: 'email', label: 'Email', type: 'email' }, { name: 'description', label: 'Description' }],
    d || {}, out => save('/api/departments', d, out));
}
function empForm(e) {
  openForm(e ? 'Edit employee' : 'New employee', [
    { name: 'fullName', label: 'Full name', required: true }, { name: 'email', label: 'Email', type: 'email' }, { name: 'jobTitle', label: 'Job title' },
    { name: 'active', label: 'Active', type: 'select', required: true, options: [{ v: 'true', l: 'Yes' }, { v: 'false', l: 'No' }] },
    { name: 'departmentIds', label: 'Departments (an employee can be in several)', type: 'multi', options: opts(D.depts) }],
    e ? { ...e, active: String(e.active) } : { active: 'true', departmentIds: [] },
    out => save('/api/employees', e, { ...out, active: out.active === 'true' }));
}
function ruleForm(r) {
  openForm(r ? 'Edit routing rule' : 'New routing rule', [
    { name: 'projectId', label: 'Project (optional)', type: 'select', options: opts(D.projects) },
    { name: 'componentType', label: 'Component type (optional)', type: 'select', options: enumOpts(COMP_TYPES) },
    { name: 'category', label: 'Category (optional, e.g. PAYMENT, BUG, QUESTION)' },
    { name: 'departmentId', label: 'Send to department', type: 'select', required: true, options: opts(D.depts) },
    { name: 'priority', label: 'Rule priority (higher wins on ties)', type: 'number' }],
    r || { priority: 0 }, out => save('/api/routing-rules', r, { ...out, projectId: toLong(out.projectId), departmentId: toLong(out.departmentId), priority: out.priority ?? 0 }));
}
function clientForm(c) {
  openForm(c ? 'Edit app client' : 'New app client', [
    { name: 'name', label: 'App name', required: true }, { name: 'apiKey', label: 'API key', required: true },
    { name: 'projectId', label: 'Default project', type: 'select', options: opts(D.projects) },
    { name: 'active', label: 'Active', type: 'select', required: true, options: [{ v: 'true', l: 'Yes' }, { v: 'false', l: 'No' }] }],
    c ? { ...c, active: String(c.active) } : { apiKey: 'hub_' + crypto.randomUUID().replace(/-/g, ''), active: 'true' },
    out => save('/api/app-clients', c, { ...out, projectId: toLong(out.projectId), active: out.active === 'true' }));
}
let USERS = [];
function userForm(u) {
  const fields = [];
  if (!u) fields.push({ name: 'username', label: 'Username', required: true });
  fields.push({ name: 'password', label: u ? 'New password (leave empty to keep)' : 'Password (min 8 characters)', type: 'password', required: !u },
    { name: 'fullName', label: 'Full name', required: true }, { name: 'email', label: 'Email', type: 'email' },
    { name: 'role', label: 'Role', type: 'select', required: true, options: ROLES.map(r => ({ v: r, l: ROLE_LABEL[r] })) },
    { name: 'employeeId', label: 'Linked employee (for staff roles)', type: 'select', options: opts(D.emps, 'fullName') },
    { name: 'partnerId', label: 'Linked partner (for customers)', type: 'select', options: opts(D.partners) },
    { name: 'active', label: 'Active', type: 'select', required: true, options: [{ v: 'true', l: 'Yes' }, { v: 'false', l: 'No (cannot log in)' }] });
  openForm(u ? 'Edit user' : 'New user', fields, u ? { ...u, active: String(u.active) } : { role: 'EMPLOYEE', active: 'true' }, out => {
    const body = { ...out, employeeId: toLong(out.employeeId), partnerId: toLong(out.partnerId), active: out.active === 'true' };
    if (!body.password) delete body.password;
    return u ? api('/api/users/' + u.id, 'PUT', body) : api('/api/users', 'POST', body);
  });
}

/* ---------- actions (buttons use data-act) ---------- */
const act = {
  'new-project': () => projectForm(),
  'edit-project': id => projectForm(find(D.projects, id)),
  'del-project': id => del('/api/projects/' + id),
  'add-component': (id, b) => componentForm(Number(b.ctx)),
  'edit-component': id => { const c = find(D.components, id); componentForm(c.projectId, c); },
  'del-component': id => del('/api/components/' + id),
  'sell-project': (id, b) => saleForm({ projectId: Number(b.ctx) }),
  'sell-partner': (id, b) => saleForm({ partnerId: Number(b.ctx) }),
  'edit-sale': id => { const s = find(D.sales, id); saleForm(s.projectId && location.hash.startsWith('#/projects') ? { projectId: s.projectId } : { partnerId: s.partnerId }, s); },
  'del-sale': id => del('/api/sales/' + id),
  'new-partner': () => partnerForm(),
  'edit-partner': id => partnerForm(find(D.partners, id)),
  'del-partner': id => del('/api/partners/' + id),
  'kyc-edit': (id, b) => kycForm(Number(b.ctx), id ? find(D.kycs, id) : null),
  'kyc-submit': async id => { await api(`/api/kyc/${id}/submit`, 'POST'); toast('KYC submitted'); refresh(); },
  'kyc-approve': id => review(id, 'APPROVED'),
  'kyc-reject': id => review(id, 'REJECTED'),
  'kyc-ai': id => kycAi(id),
  'doc-add': (id, b) => docForm(Number(b.ctx)),
  'doc-del': id => del('/api/kyc-documents/' + id),
  'new-ticket': () => ticketForm(),
  'new-task': () => taskForm(),
  'edit-task': id => api('/api/tasks/' + id).then(t => USER.role === 'EMPLOYEE' ? statusForm(t) : taskForm(t)),
  'del-task': id => del('/api/tasks/' + id),
  'new-dept': () => deptForm(),
  'edit-dept': id => deptForm(find(D.depts, id)),
  'del-dept': id => del('/api/departments/' + id),
  'new-emp': () => empForm(),
  'edit-emp': id => empForm(find(D.emps, id)),
  'del-emp': id => del('/api/employees/' + id),
  'new-rule': () => ruleForm(),
  'edit-rule': id => api('/api/routing-rules/' + id).then(ruleForm),
  'del-rule': id => del('/api/routing-rules/' + id),
  'new-client': () => clientForm(),
  'edit-client': id => api('/api/app-clients/' + id).then(clientForm),
  'del-client': id => del('/api/app-clients/' + id),
  'new-user': () => userForm(),
  'edit-user': id => userForm(find(USERS, id)),
  'del-user': id => del('/api/users/' + id),
  /* portal */
  'p-chat': () => PCHAT && PCHAT.open(),
  'p-ask': (id, b) => { PCHAT.open(); PCHAT.send('Tell me about ' + b.ctx); },
  'p-help': () => { location.hash = '#/help'; },
  'p-help-for': id => { HELP_PROJECT = id; location.hash = '#/help'; },
  'p-login': () => openAuth('login'),
  'p-register': () => openAuth('register'),
};
async function review(id, status) {
  const notes = prompt(status === 'APPROVED' ? 'Approval note (optional)' : 'Reason for rejection');
  if (notes === null) return;
  try { await api(`/api/kyc/${id}/review`, 'POST', { status, notes }); refresh(); } catch (e) { toast(e.message, true); }
}
async function kycAi(id) {
  toast('AI is reviewing the KYC file…');
  try {
    const r = await api(`/api/kyc/${id}/ai-review`, 'POST');
    const issues = (r.issues || []).map(i => `<tr><td>${esc(i.field)}</td><td>${esc(i.issue)}</td><td>${badge(i.severity)}</td></tr>`).join('');
    showInfo('AI KYC review', `<div class="row">Risk ${badge(r.riskLevel)} Recommendation ${badge(r.recommendation)}</div><p>${esc(r.summary)}</p>
      ${issues ? `<table><thead><tr><th>Field</th><th>Issue</th><th>Severity</th></tr></thead><tbody>${issues}</tbody></table>` : '<p class="muted">No issues found in the submitted data.</p>'}
      ${(r.missingItems || []).length ? `<p><b>Missing:</b> ${(r.missingItems).map(esc).join(', ')}</p>` : ''}
      <p class="muted">${esc(r.reasoning)}</p><p class="muted"><i>AI assists the officer; the decision is always human. Only the submitted data was checked, not the document images.</i></p>`);
  } catch (e) { toast(e.message, true); }
}
document.addEventListener('click', e => {
  const b = e.target.closest('[data-act]');
  if (!b) return;
  const fn = act[b.dataset.act];
  if (fn) { const id = b.dataset.id === '' ? null : Number(b.dataset.id); Promise.resolve(fn(id, b.dataset)).catch(err => toast(err.message, true)); }
});

/* inline forms (data-form) */
const forms = {
  async reply(f) {
    const body = f.body.value.trim();
    if (!body) return;
    await api(`/api/tickets/${f.dataset.id}/messages`, 'POST', { body, internal: f.internal.checked });
    route();
  },
  async ticketUpdate(f) {
    const u = {};
    for (const k of ['status', 'priority']) if (f[k].value) u[k] = f[k].value;
    for (const k of ['assigneeId', 'departmentId']) if (f[k].value) u[k] = Number(f[k].value);
    await api(`/api/tickets/${f.dataset.id}`, 'PATCH', u);
    toast('Ticket updated');
    route();
  },
  async settingDept(f) {
    await api('/api/settings', 'PUT', { key: 'default_ticket_department', value: f.value.value });
    toast('Default department saved');
  },
};
document.addEventListener('submit', e => {
  const f = e.target.closest('[data-form]');
  if (!f) return;
  e.preventDefault();
  forms[f.dataset.form](f).catch(err => toast(err.message, true));
});

/* ======================= STAFF VIEWS ======================= */
const view = html => { $('#view').innerHTML = html; };
const W = a => can(a, 'w');

function ticketTable(rows) {
  return table([
    { h: 'Ref', f: t => `<a href="#/tickets/${t.id}">${esc(t.reference)}</a>` },
    { h: 'Subject', f: t => esc(t.subject) },
    { h: 'Project', f: t => esc(t.projectId ? nameOf(D.projects, t.projectId) : '—') },
    { h: 'Department', f: t => esc(t.departmentId ? nameOf(D.depts, t.departmentId) : '—') },
    { h: 'Priority', f: t => badge(t.priority) }, { h: 'Status', f: t => badge(t.status) },
    { h: 'Assignee', f: t => esc(empName(t.assigneeId)) }, { h: 'Created', f: t => fmtTime(t.createdAt) }], rows);
}

async function dashboardView() {
  const s = await api('/api/dashboard');
  const tickets = can('tickets') ? await api('/api/tickets') : [];
  const myTasks = USER.employeeId && can('tasks') ? (await api('/api/tasks?employeeId=' + USER.employeeId)).filter(t => ['IN_PROGRESS', 'BLOCKED', 'PLANNED'].includes(t.status)) : [];
  const stats = [[s.projects, 'Projects'], [s.partners, 'Partners'], [s.employees, 'Employees']];
  if (s.openTickets !== undefined) stats.push([s.openTickets, 'Open tickets']);
  stats.push([s.ongoingTasks, 'Ongoing tasks']);
  if (s.salesTotal !== undefined) stats.push([money(s.salesTotal, s.salesCurrency), 'Total sales']);
  let bars = '';
  if (s.openTicketsByDepartment) {
    const max = Math.max(1, ...Object.values(s.openTicketsByDepartment));
    bars = `<div class="card"><b>Open tickets by department</b>${Object.entries(s.openTicketsByDepartment).map(([k, v]) => `<div class="bar"><span style="width:90px">${esc(k)}</span><i style="width:${v / max * 220}px"></i><b>${v}</b></div>`).join('')}</div>`;
  }
  view(head(`Welcome, ${esc(USER.fullName)}`, badge(USER.role)) +
    `<div class="grid">${stats.map(([v, l]) => `<div class="stat"><b>${typeof v === 'string' ? esc(v) : v}</b><span>${l}</span></div>`).join('')}</div>${bars}` +
    (myTasks.length ? `<h3>My tasks</h3>` + table([{ h: 'Task', f: t => esc(t.title) }, { h: 'Project', f: t => esc(t.projectId ? nameOf(D.projects, t.projectId) : '—') }, { h: 'Status', f: t => badge(t.status) }, { h: 'Due', f: t => fmtDate(t.dueDate) }], myTasks, t => btn('edit-task', t.id, 'Update')) : '') +
    (can('tickets') ? `<h3>Latest tickets</h3>${ticketTable(tickets.slice(0, 6))}` : ''));
}

function projectsView() {
  const cols = [
    { h: 'Project', f: p => `<a href="#/projects/${p.id}"><b>${esc(p.name)}</b></a><div class="muted">${esc(p.description)}</div>` },
    { h: 'Status', f: p => badge(p.status) + (p.publicVisible ? '' : ' <span class="badge">internal</span>') },
    { h: 'Components', f: p => D.components.filter(c => c.projectId === p.id).map(c => badge(c.type)).join('') || '—' }];
  if (can('sales')) cols.push({ h: 'Sold to', f: p => D.sales.filter(s => s.projectId === p.id).map(s => `<span class="badge">${esc(nameOf(D.partners, s.partnerId))}</span>`).join('') || '—' });
  view(head('Projects', W('projects') ? btn('new-project', '', '+ New project', 'primary') : '') +
    table(cols, D.projects, W('projects') ? p => btn('edit-project', p.id, 'Edit') + ' ' + btn('del-project', p.id, 'Delete', 'danger') : null));
}

async function projectDetail(id) {
  const p = find(D.projects, id);
  if (!p) return view('<div class="error">Project not found</div>');
  const [tasks, tickets] = await Promise.all([can('tasks') ? api('/api/tasks?projectId=' + id) : [], can('tickets') ? api('/api/tickets?projectId=' + id) : []]);
  let html = head(esc(p.name) + ' ' + badge(p.status), `<a href="#/projects">&larr; All projects</a> ` + (W('projects') ? btn('edit-project', id, 'Edit') : '')) +
    `<p class="muted">${esc(p.description)}</p>
    <div class="head"><h3>Components</h3>${W('projects') ? btn('add-component', '', '+ Add component', 'primary', id) : ''}</div>` +
    table([{ h: 'Type', f: c => badge(c.type) }, { h: 'Name', f: c => esc(c.name) }, { h: 'Stack', f: c => esc(c.stack) }, { h: 'Version', f: c => esc(c.version) },
      { h: 'Repository', f: c => c.repoUrl ? `<a href="${esc(c.repoUrl)}" target="_blank" rel="noopener">${esc(c.repoUrl)}</a>` : '—' },
      { h: 'Environments', f: c => esc(c.environmentUrls).replace(/\n/g, '<br>') }],
      D.components.filter(c => c.projectId === id), W('projects') ? c => btn('edit-component', c.id, 'Edit') + ' ' + btn('del-component', c.id, 'Delete', 'danger') : null);
  if (can('sales')) {
    html += `<div class="head"><h3>Sold to partners</h3>${W('sales') ? btn('sell-project', '', '+ Sell to partner', 'primary', id) : ''}</div>` +
      table([{ h: 'Partner', f: s => `<a href="#/partners/${s.partnerId}">${esc(nameOf(D.partners, s.partnerId))}</a>` }, { h: 'Sold on', f: s => fmtDate(s.soldAt) },
        { h: 'Amount', f: s => money(s.amount, s.currency) }, { h: 'KYC', f: s => { const k = D.kycs.find(x => x.partnerId === s.partnerId); return k ? badge(k.status) : '—'; } }, { h: 'Status', f: s => badge(s.status) }],
        D.sales.filter(s => s.projectId === id), W('sales') ? s => btn('edit-sale', s.id, 'Edit') + ' ' + btn('del-sale', s.id, 'Remove', 'danger') : null);
  }
  if (can('tasks')) html += `<h3>Tasks</h3>` + table([{ h: 'Task', f: t => esc(t.title) }, { h: 'Assignee', f: t => esc(empName(t.assigneeId)) }, { h: 'Status', f: t => badge(t.status) }, { h: 'Due', f: t => fmtDate(t.dueDate) }], tasks);
  if (can('tickets')) html += `<h3>Tickets</h3>` + ticketTable(tickets);
  view(html);
}

function partnersView() {
  const cols = [{ h: 'Partner', f: p => `<a href="#/partners/${p.id}"><b>${esc(p.name)}</b></a>` }, { h: 'Country', f: p => esc(p.country) }, { h: 'Contact', f: p => esc(p.contactEmail) }];
  if (can('kyc')) cols.push({ h: 'KYC', f: p => { const k = D.kycs.find(x => x.partnerId === p.id); return k ? badge(k.status) : '<span class="muted">none</span>'; } });
  if (can('sales')) cols.push({ h: 'Projects', f: p => D.sales.filter(s => s.partnerId === p.id).length });
  view(head('Partners', W('partners') ? btn('new-partner', '', '+ New partner', 'primary') : '') +
    table(cols, D.partners, W('partners') ? p => btn('edit-partner', p.id, 'Edit') + ' ' + btn('del-partner', p.id, 'Delete', 'danger') : null));
}

async function partnerDetail(id) {
  const p = find(D.partners, id);
  if (!p) return view('<div class="error">Partner not found</div>');
  let html = head(esc(p.name), `<a href="#/partners">&larr; All partners</a> ` + (W('partners') ? btn('edit-partner', id, 'Edit') : '')) +
    `<p class="muted">${esc(p.country)} · ${esc(p.contactEmail)} · Hub partner ID <b>${p.id}</b></p>`;
  if (can('kyc')) {
    const k = D.kycs.find(x => x.partnerId === id);
    const docs = k ? (await api('/api/kyc-documents')).filter(d => d.kycId === k.id) : [];
    const wk = W('kyc');
    html += k ? `<div class="card"><div class="row"><b>KYC</b> ${badge(k.status)}<span class="muted">submitted ${fmtDate(k.submittedAt)} · reviewed ${fmtDate(k.reviewedAt)}</span></div>
        <p><b>${esc(k.legalName)}</b><br>Registration: ${esc(k.registrationNumber)}<br>Address: ${esc(k.address)}<br>Representative: ${esc(k.representativeName)}<br>${k.notes ? 'Notes: ' + esc(k.notes) : ''}</p>
        ${wk ? `<div class="row">${btn('kyc-ai', k.id, '✨ AI review', 'primary')} ${btn('kyc-edit', k.id, 'Edit KYC', '', id)} ${btn('kyc-submit', k.id, 'Mark submitted')} ${btn('kyc-approve', k.id, 'Approve')} ${btn('kyc-reject', k.id, 'Reject', 'danger')}</div>` : ''}</div>
        <div class="head"><h3>KYC documents</h3>${wk ? btn('doc-add', '', '+ Add document', '', k.id) : ''}</div>` +
      table([{ h: 'Type', f: d => esc(d.docType) }, { h: 'File', f: d => d.url ? `<a href="${esc(d.url)}" target="_blank" rel="noopener">${esc(d.fileName || d.url)}</a>` : esc(d.fileName) }], docs, wk ? d => btn('doc-del', d.id, 'Delete', 'danger') : null)
      : `<div class="card">No KYC on file. ${wk ? btn('kyc-edit', '', 'Create KYC', 'primary', id) : ''}</div>`;
  }
  if (can('sales')) {
    html += `<div class="head"><h3>Projects bought</h3>${W('sales') ? btn('sell-partner', '', '+ Add project', 'primary', id) : ''}</div>` +
      table([{ h: 'Project', f: s => `<a href="#/projects/${s.projectId}">${esc(nameOf(D.projects, s.projectId))}</a>` }, { h: 'Sold on', f: s => fmtDate(s.soldAt) }, { h: 'Amount', f: s => money(s.amount, s.currency) }, { h: 'Status', f: s => badge(s.status) }],
        D.sales.filter(s => s.partnerId === id), W('sales') ? s => btn('edit-sale', s.id, 'Edit') + ' ' + btn('del-sale', s.id, 'Remove', 'danger') : null);
  }
  view(html);
}

let ticketFilter = { status: '', department: '' };
async function ticketsView() {
  const q = new URLSearchParams();
  if (ticketFilter.status) q.set('status', ticketFilter.status);
  if (ticketFilter.department) q.set('departmentId', ticketFilter.department);
  const rows = await api('/api/tickets?' + q);
  view(head('Tickets', btn('new-ticket', '', '+ New ticket', 'primary')) +
    `<div class="row inline" style="margin-bottom:10px">
      <select id="fStatus"><option value="">All statuses</option>${TICKET_STATUS.map(s => `<option ${ticketFilter.status === s ? 'selected' : ''}>${s}</option>`).join('')}</select>
      <select id="fDept"><option value="">All departments</option>${D.depts.map(d => `<option value="${d.id}" ${String(ticketFilter.department) === String(d.id) ? 'selected' : ''}>${esc(d.name)}</option>`).join('')}</select></div>` + ticketTable(rows));
  $('#fStatus').onchange = e => { ticketFilter.status = e.target.value; ticketsView(); };
  $('#fDept').onchange = e => { ticketFilter.department = e.target.value; ticketsView(); };
}

async function ticketDetail(id) {
  const { ticket: t, messages } = await api('/api/tickets/' + id);
  const sel = (name, options, cur) => `<select name="${name}"><option value="">—</option>${options.map(o => `<option value="${esc(o.v)}" ${String(o.v) === String(cur ?? '') ? 'selected' : ''}>${esc(o.l)}</option>`).join('')}</select>`;
  const bubble = m => `<div class="tb ${m.authorRole === 'EXTERNAL' ? 'me' : ''} ${m.internal ? 'internal' : ''}"><small>${esc(m.author || 'Unknown')}${m.authorRole ? ' · ' + esc(m.authorRole) : ''}${m.internal ? ' · INTERNAL NOTE' : ''} · ${fmtTime(m.createdAt)}</small>${esc(m.body).replace(/\n/g, '<br>')}</div>`;
  view(head(`${esc(t.reference)} · ${esc(t.subject)}`, `<a href="#/tickets">&larr; All tickets</a>`) +
    `<div class="card"><div class="row">${badge(t.status)} ${badge(t.priority)} <span class="muted">${esc(t.category)} · from ${esc(t.source)} · ${fmtTime(t.createdAt)}</span></div>
      <p>${esc(t.description).replace(/\n/g, '<br>')}</p>
      <p class="muted">Reporter: ${esc(t.reporterName)} ${esc(t.reporterContact)} · Project: ${esc(t.projectId ? nameOf(D.projects, t.projectId) : '—')} · Partner: ${esc(t.partnerId ? nameOf(D.partners, t.partnerId) : '—')}${t.requesterUserId ? ' · registered customer' : ''}</p></div>
    <form class="card inline row" data-form="ticketUpdate" data-id="${id}">
      <label>Status${sel('status', enumOpts(TICKET_STATUS), t.status)}</label><label>Priority${sel('priority', enumOpts(PRIORITY), t.priority)}</label>
      <label>Assignee${sel('assigneeId', opts(D.emps, 'fullName'), t.assigneeId)}</label><label>Department${sel('departmentId', opts(D.depts), t.departmentId)}</label>
      <button class="primary">Update</button></form>
    <h3>Conversation</h3><div class="thread">${messages.map(bubble).join('') || '<p class="muted">No messages yet.</p>'}</div>
    <form class="inline" data-form="reply" data-id="${id}"><textarea name="body" placeholder="Write a reply to the customer, or an internal note..."></textarea>
      <div class="actions" style="align-items:center"><label class="chk"><input type="checkbox" name="internal"> Internal note (hidden from the customer)</label><button class="primary">Send</button></div></form>`);
}

let taskEmp = null;
async function tasksView() {
  if (taskEmp === null) taskEmp = USER.role === 'EMPLOYEE' && USER.employeeId ? String(USER.employeeId) : '';
  const rows = await api('/api/tasks' + (taskEmp ? '?employeeId=' + taskEmp : ''));
  const bucket = s => rows.filter(t => (s === 'PAST' ? ['DONE', 'CANCELLED'] : s === 'ONGOING' ? ['IN_PROGRESS', 'BLOCKED'] : ['PLANNED']).includes(t.status));
  const mine = t => USER.role === 'EMPLOYEE' && USER.employeeId === t.assigneeId;
  const card = t => `<div class="task"><b>${esc(t.title)}</b> ${badge(t.status)}<small>${esc(empName(t.assigneeId))} · ${esc(t.projectId ? nameOf(D.projects, t.projectId) : 'No project')}</small>
    <small>${fmtDate(t.startDate)} → ${fmtDate(t.dueDate)}${t.completedAt ? ' · done ' + fmtDate(t.completedAt) : ''}</small>
    <div class="row" style="margin-top:6px">${W('tasks') || mine(t) ? btn('edit-task', t.id, W('tasks') ? 'Edit' : 'Update status') : ''} ${W('tasks') ? btn('del-task', t.id, 'Delete', 'danger') : ''}</div></div>`;
  const col = (title, key) => `<div class="col"><h3>${title} (${bucket(key).length})</h3>${bucket(key).map(card).join('') || '<p class="muted">Nothing</p>'}</div>`;
  view(head('Tasks', W('tasks') ? btn('new-task', '', '+ New task', 'primary') : '') +
    `<div class="inline" style="margin-bottom:10px"><select id="fEmp"><option value="">All employees</option>${D.emps.map(e => `<option value="${e.id}" ${String(taskEmp) === String(e.id) ? 'selected' : ''}>${esc(e.fullName)}</option>`).join('')}</select></div>
    <div class="cols">${col('Past', 'PAST')}${col('Ongoing', 'ONGOING')}${col('Future', 'FUTURE')}</div>`);
  $('#fEmp').onchange = e => { taskEmp = e.target.value; tasksView(); };
}

function teamView() {
  const w = W('team');
  view(head('Departments', w ? btn('new-dept', '', '+ New department', 'primary') : '') + table([
    { h: 'Department', f: d => `<b>${esc(d.name)}</b><div class="muted">${esc(d.description)}</div>` }, { h: 'Email', f: d => esc(d.email) },
    { h: 'Members', f: d => D.emps.filter(e => e.departmentIds.includes(d.id)).map(e => `<span class="badge">${esc(e.fullName)}</span>`).join('') || '—' }],
    D.depts, w ? d => btn('edit-dept', d.id, 'Edit') + ' ' + btn('del-dept', d.id, 'Delete', 'danger') : null) +
    `<div class="head" style="margin-top:20px"><h2>Employees</h2>${w ? btn('new-emp', '', '+ New employee', 'primary') : ''}</div>` + table([
      { h: 'Name', f: e => `<b>${esc(e.fullName)}</b><div class="muted">${esc(e.jobTitle)}</div>` }, { h: 'Email', f: e => esc(e.email) },
      { h: 'Departments', f: e => e.departmentIds.map(i => `<span class="badge">${esc(nameOf(D.depts, i))}</span>`).join('') || '—' }, { h: 'Active', f: e => e.active ? 'Yes' : 'No' }],
      D.emps, w ? e => btn('edit-emp', e.id, 'Edit') + ' ' + btn('del-emp', e.id, 'Delete', 'danger') : null));
}

/* ---- AI delivery accelerator ---- */
let ACC = null, accTab = 'stories', accStatus = {};
const SAMPLE = `Wallet top-up service for a microfinance bank (v1)

Customers can top up their mobile wallet from their bank account.
- POST /v1/topups: body {accountId, walletMsisdn, amount, currency}. Amount min 500 XAF, max 500,000 XAF per transaction, 2,000,000 XAF per day per account.
- The client sends an Idempotency-Key header; the same key must never debit twice.
- The bank core system is reached through a SOAP service (operation DebitAccount). Timeouts must not leave the customer debited without a wallet credit: failed credits are reversed automatically.
- GET /v1/topups/{id} returns the status: PENDING, SUCCESS, FAILED, REVERSED.
- Only authenticated customers can see their own top-ups; back-office agents with role SUPERVISOR can see all and export a daily CSV.
- Every operation is audited (who, when, amount, result). SMS confirmation is sent to the customer on success.`;

async function acceleratorView() {
  view(head('AI Delivery Accelerator') + `<p class="muted">Paste a client's requirements or an API specification (OpenAPI/JSON, SOAP/WSDL, or plain text). The AI generates user stories, API documentation, test cases and a test report you can execute.</p>
    <div class="card inline"><textarea id="accText" style="min-height:190px" placeholder="Paste requirements or a specification here..."></textarea>
    <div class="row" style="margin-top:8px"><button class="primary" id="accGo">✨ Generate delivery pack</button><button id="accSample">Load a sample</button><span class="muted" id="accMsg"></span></div></div><div id="accOut"></div>`);
  $('#accSample').onclick = () => { $('#accText').value = SAMPLE; };
  $('#accGo').onclick = async () => {
    const text = $('#accText').value.trim();
    if (text.length < 40) return toast('Paste a longer text first', true);
    $('#accGo').disabled = true; $('#accMsg').textContent = 'Generating… this can take up to a minute.';
    try { ACC = (await api('/api/accelerator/generate', 'POST', { text })).result; accStatus = {}; accTab = 'stories'; renderAcc(); }
    catch (e) { toast(e.message, true); }
    $('#accGo').disabled = false; $('#accMsg').textContent = '';
  };
  if (ACC) renderAcc();
}
function storyText(s) { return `As a ${s.asA}, I want ${s.iWant}, so that ${s.soThat}.\n\nAcceptance criteria:\n` + (s.acceptanceCriteria || []).map(c => '- ' + c).join('\n'); }
function reportStats() {
  const tcs = ACC.testCases || [];
  const c = { pass: 0, fail: 0, blocked: 0, notrun: 0 };
  tcs.forEach(t => { const s = accStatus[t.id] || 'notrun'; c[s]++; });
  const run = c.pass + c.fail + c.blocked;
  return { ...c, total: tcs.length, rate: run ? Math.round(c.pass / run * 100) : 0 };
}
function reportMd() {
  const st = reportStats();
  return `# Test report\n\n${ACC.summary || ''}\n\n- Total: ${st.total} · Passed: ${st.pass} · Failed: ${st.fail} · Blocked: ${st.blocked} · Not run: ${st.notrun} · Pass rate: ${st.rate}%\n\n| ID | Title | Type | Priority | Story | Status |\n|---|---|---|---|---|---|\n` +
    (ACC.testCases || []).map(t => `| ${t.id} | ${t.title} | ${t.type} | ${t.priority} | ${t.relatedStory || ''} | ${accStatus[t.id] || 'notrun'} |`).join('\n');
}
function packMd() {
  return `# ${ACC.summary || 'Delivery pack'}\n\n## User stories\n\n` + (ACC.userStories || []).map(s => `### ${s.title} (${s.priority})\n${storyText(s)}\n`).join('\n') +
    `\n## API documentation\n\n${ACC.apiDocumentation || ''}\n\n## Test cases\n\n` + (ACC.testCases || []).map(t => `### ${t.id} ${t.title} (${t.type}, ${t.priority})\nSteps:\n${(t.steps || []).map((x, i) => `${i + 1}. ${x}`).join('\n')}\n\nExpected: ${t.expected}\n`).join('\n') +
    `\n## Risks and open questions\n\n` + (ACC.risksAndQuestions || []).map(r => '- ' + r).join('\n') + '\n\n' + reportMd();
}
function renderAcc() {
  const tabs = [['stories', 'User stories'], ['api', 'API documentation'], ['tests', 'Test cases'], ['report', 'Test report']];
  let body = '';
  if (accTab === 'stories') {
    body = `<p>${esc(ACC.summary)}</p>` + (ACC.userStories || []).map(s => `<div class="story"><b>${esc(s.title)}</b> ${badge(s.priority)}<div>As a <b>${esc(s.asA)}</b>, I want ${esc(s.iWant)}, so that ${esc(s.soThat)}.</div><ul>${(s.acceptanceCriteria || []).map(c => `<li>${esc(c)}</li>`).join('')}</ul></div>`).join('') +
      ((ACC.risksAndQuestions || []).length ? `<h3>Risks and open questions</h3><ul>${ACC.risksAndQuestions.map(r => `<li>${esc(r)}</li>`).join('')}</ul>` : '') +
      (can('tasks', 'w') ? `<div class="card inline row"><select id="accProj"><option value="">Choose a project…</option>${D.projects.map(p => `<option value="${p.id}">${esc(p.name)}</option>`).join('')}</select><button class="primary" id="accTasks">Create ${(ACC.userStories || []).length} tasks from these stories</button></div>` : '');
  } else if (accTab === 'api') body = ACC.apiDocumentation ? md(ACC.apiDocumentation) : '<p class="muted">No API found in the input.</p>';
  else if (accTab === 'tests') body = table([{ h: 'ID', f: t => esc(t.id) }, { h: 'Test', f: t => `<b>${esc(t.title)}</b><div class="muted">${esc(t.relatedStory)}</div>` }, { h: 'Type', f: t => badge(t.type) }, { h: 'Priority', f: t => badge(t.priority) },
    { h: 'Steps and expected result', f: t => `<ol style="margin:0;padding-left:18px">${(t.steps || []).map(x => `<li>${esc(x)}</li>`).join('')}</ol><div><b>Expected:</b> ${esc(t.expected)}</div>` }], ACC.testCases || []);
  else {
    const st = reportStats();
    body = `<div class="grid"><div class="stat"><b>${st.total}</b><span>Test cases</span></div><div class="stat"><b>${st.pass}</b><span>Passed</span></div><div class="stat"><b>${st.fail}</b><span>Failed</span></div><div class="stat"><b>${st.rate}%</b><span>Pass rate</span></div></div>` +
      table([{ h: 'ID', f: t => esc(t.id) }, { h: 'Test', f: t => esc(t.title) }, { h: 'Priority', f: t => badge(t.priority) },
        { h: 'Result', f: t => `<select class="sm-select" data-tc="${esc(t.id)}">${[['notrun', 'Not run'], ['pass', 'Pass'], ['fail', 'Fail'], ['blocked', 'Blocked']].map(([v, l]) => `<option value="${v}" ${(accStatus[t.id] || 'notrun') === v ? 'selected' : ''}>${l}</option>`).join('')}</select>` }], ACC.testCases || []) +
      '<p class="muted">Execute the tests, set each result, and download the report.</p>';
  }
  $('#accOut').innerHTML = `<div class="tabs">${tabs.map(([k, l]) => `<button class="${accTab === k ? 'on' : ''}" data-tab="${k}">${l}</button>`).join('')}<span class="spacer"></span><button id="accDl">⬇ Download all (.md)</button></div>${body}`;
  $('#accOut').querySelectorAll('[data-tab]').forEach(b => { b.onclick = () => { accTab = b.dataset.tab; renderAcc(); }; });
  $('#accDl').onclick = () => download('delivery-pack.md', packMd());
  $('#accOut').querySelectorAll('[data-tc]').forEach(s => { s.onchange = () => { accStatus[s.dataset.tc] = s.value; renderAcc(); }; });
  const tb = $('#accTasks');
  if (tb) tb.onclick = async () => {
    const pid = Number($('#accProj').value);
    if (!pid) return toast('Choose a project first', true);
    tb.disabled = true;
    try {
      for (const s of ACC.userStories || []) await api('/api/tasks', 'POST', { title: s.title, description: storyText(s), projectId: pid, status: 'PLANNED' });
      toast(`${(ACC.userStories || []).length} tasks created in the Hub`);
    } catch (e) { toast(e.message, true); }
    tb.disabled = false;
  };
}

/* ---- users and roles (admin) ---- */
async function usersView() {
  USERS = await api('/api/users');
  const areas = [['projects', 'Projects and components'], ['partners', 'Partners'], ['sales', 'Sales and amounts'], ['kyc', 'Partner KYC'], ['team', 'Employees and departments'], ['tickets', 'Tickets'], ['tasks', 'Tasks'], ['accelerator', 'AI accelerator'], ['settings', 'Settings, routing, app keys'], ['users', 'Users and roles']];
  const cell = (a, r) => PERM[a].w.includes(r) ? '<span class="lvl-w">W</span>' : PERM[a].r.includes(r) ? '<span class="lvl-r">R</span>' : '·';
  view(head('Users and roles', btn('new-user', '', '+ New user', 'primary')) +
    table([{ h: 'User', f: u => `<b>${esc(u.fullName)}</b><div class="muted">${esc(u.username)}</div>` }, { h: 'Role', f: u => badge(u.role) },
      { h: 'Linked to', f: u => esc(u.employeeId ? empName(u.employeeId) : u.partnerId ? nameOf(D.partners, u.partnerId) : '—') }, { h: 'Active', f: u => u.active ? 'Yes' : 'No' }],
      USERS, u => btn('edit-user', u.id, 'Edit') + ' ' + btn('del-user', u.id, 'Delete', 'danger')) +
    `<h3>What each role can do</h3><table class="matrix"><thead><tr><th></th>${STAFF_ROLES.map(r => `<th>${ROLE_LABEL[r]}</th>`).join('')}</tr></thead><tbody>${areas.map(([a, l]) => `<tr><td>${l}</td>${STAFF_ROLES.map(r => `<td>${cell(a, r)}</td>`).join('')}</tr>`).join('')}</tbody></table>
    <p class="muted"><span class="lvl-w">W</span> read and change · <span class="lvl-r">R</span> read only · Employees only see tickets of their departments and can update the status of their own tasks. Staff AI assistant tools follow the same rules.<br>
    <b>Customers</b> (external users) see the public catalog, use the AI assistant, send help requests and chat with the team about their own requests. <b>Visitors</b> (not logged in) see the catalog, use the assistant and send a request anonymously; they must log in to read replies.</p>`);
}

async function settingsView() {
  const [settings, rules, clients] = await Promise.all([api('/api/settings'), api('/api/routing-rules'), api('/api/app-clients')]);
  view(head('Settings') +
    `<div class="card"><b>Ticket routing</b><p class="muted">When an app, the portal or the AI assistant creates a ticket, the most specific matching rule decides which department is notified (project, then component type, then category such as BUG, QUESTION, HELP). If nothing matches, the default department below is used.</p>
    <form class="row inline" data-form="settingDept"><label style="margin:0">Default department<select name="value">${D.depts.map(d => `<option value="${d.id}" ${String(settings.default_ticket_department) === String(d.id) ? 'selected' : ''}>${esc(d.name)}</option>`).join('')}</select></label><button class="primary">Save</button></form></div>
    <div class="head"><h3>Routing rules</h3>${btn('new-rule', '', '+ New rule', 'primary')}</div>` +
    table([{ h: 'Project', f: r => esc(r.projectId ? nameOf(D.projects, r.projectId) : 'Any') }, { h: 'Component', f: r => r.componentType ? badge(r.componentType) : 'Any' },
      { h: 'Category', f: r => esc(r.category || 'Any') }, { h: 'Department', f: r => `<b>${esc(nameOf(D.depts, r.departmentId))}</b>` }, { h: 'Priority', f: r => r.priority }],
      rules, r => btn('edit-rule', r.id, 'Edit') + ' ' + btn('del-rule', r.id, 'Delete', 'danger')) +
    `<div class="head"><h3>Connected apps (API keys)</h3>${btn('new-client', '', '+ New app', 'primary')}</div>
    <p class="muted">Apps call <code>POST /api/public/tickets</code> with header <code>X-API-Key</code> instead of sending users to WhatsApp.</p>` +
    table([{ h: 'App', f: c => esc(c.name) }, { h: 'Default project', f: c => esc(c.projectId ? nameOf(D.projects, c.projectId) : '—') }, { h: 'API key', f: c => `<code>${esc(c.apiKey)}</code>` }, { h: 'Active', f: c => c.active ? 'Yes' : 'No' }],
      clients, c => btn('edit-client', c.id, 'Edit') + ' ' + btn('del-client', c.id, 'Delete', 'danger')));
}

/* ---- staff router ---- */
const STAFF_NAV = [['dashboard', 'Dashboard', () => true], ['projects', 'Projects', () => can('projects')], ['partners', 'Partners', () => can('partners')],
  ['tickets', 'Tickets', () => can('tickets')], ['tasks', 'Tasks', () => can('tasks')], ['team', 'Team', () => can('team')],
  ['accelerator', 'Accelerator', () => can('accelerator')], ['users', 'Users', () => can('users')], ['settings', 'Settings', () => can('settings')]];
const staffViews = {
  dashboard: dashboardView, projects: id => id ? projectDetail(id) : projectsView(), partners: id => id ? partnerDetail(id) : partnersView(),
  tickets: id => id ? ticketDetail(id) : ticketsView(), tasks: tasksView, team: teamView, accelerator: acceleratorView, users: usersView, settings: settingsView,
};
async function staffRoute() {
  const [page, id] = location.hash.replace(/^#\/?/, '').split('/');
  const nav = STAFF_NAV.filter(n => n[2]());
  const key = nav.some(n => n[0] === page) ? page : 'dashboard';
  $('#nav').innerHTML = nav.map(([k, l]) => `<a href="#/${k}" class="${k === key ? 'on' : ''}">${l}</a>`).join('');
  try { await staffViews[key](id ? Number(id) : null); } catch (e) { view(`<div class="error">${esc(e.message)}</div>`); }
}

/* ---- staff notifications (live) ---- */
async function loadNotifs() {
  if (!can('tickets')) return;
  N = await api('/api/notifications');
  const unread = N.filter(n => !n.read).length;
  $('#bellCount').textContent = unread;
  $('#bellCount').classList.toggle('hidden', unread === 0);
  if (!$('#notifPanel').classList.contains('hidden')) renderPanel();
}
function renderPanel() {
  $('#notifPanel').innerHTML = `<div class="row" style="justify-content:space-between;padding:4px 8px"><b>Notifications</b><button class="sm" id="readAll">Mark all read</button></div>` +
    (N.map(n => `<div class="n ${n.read ? '' : 'unread'}" data-n="${n.id}" data-ref="${esc(n.refType)}:${n.refId}">${esc(n.title)}<div class="muted">${esc(nameOf(D.depts, n.departmentId))} · ${esc(n.body)} · ${fmtTime(n.createdAt)}</div></div>`).join('') || '<p class="muted" style="padding:8px">No notifications</p>');
  $('#readAll').onclick = async () => { await api('/api/notifications/read-all', 'POST'); loadNotifs(); };
}
function connectLive() {
  const es = new EventSource('/api/notifications/stream?access_token=' + encodeURIComponent(TOKEN));
  es.addEventListener('notification', ev => {
    const n = JSON.parse(ev.data);
    toast('🔔 ' + n.title + ' — ' + (n.body || ''));
    loadNotifs();
    if (/^#\/(tickets)?$|^$|^#\/dashboard$/.test(location.hash)) route();
  });
}

/* ======================= CHAT WIDGET (streaming text + voice) ======================= */
const LANGS = { fr: { stt: 'fr-FR', prefix: 'fr' }, en: { stt: 'en-US', prefix: 'en' }, pcm: { stt: 'en-US', prefix: 'en' } };
const GREET = {
  fr: 'Bonjour ! Je suis l\'assistant IWOMI. Posez-moi une question sur nos solutions ou décrivez votre problème. Vous pouvez écrire ou appuyer sur le micro.',
  en: 'Hello! I am the IWOMI assistant. Ask me about our solutions or tell me about a problem. You can type or tap the microphone.',
  pcm: 'How far! Na IWOMI assistant dey here. Ask me about our solutions or tell me your wahala. You fit type or press the microphone.',
};
const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
const HAS_TTS = 'speechSynthesis' in window;

/* voice settings, saved in this browser: { on, volume (0-1), rate (0.7-1.3), voices: { fr: name, en: name } } */
const VS = JSON.parse(localStorage.getItem('hub.voice') || '{}');
const saveVS = () => localStorage.setItem('hub.voice', JSON.stringify(VS));

/* reads a Server-Sent Events response from a POST request, calling handlers[event](data) as events arrive */
async function streamChat(path, body, handlers, signal) {
  const headers = { 'Content-Type': 'application/json' };
  if (TOKEN) headers['Authorization'] = 'Bearer ' + TOKEN;
  const r = await fetch(path, { method: 'POST', headers, body: JSON.stringify(body), signal });
  if (!r.ok) {
    let m = r.statusText;
    try { m = (await r.json()).error || m; } catch (e) { /* no json */ }
    if (r.status === 401 && TOKEN) logout();
    throw new Error(m);
  }
  const reader = r.body.getReader();
  const dec = new TextDecoder();
  let buf = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buf += dec.decode(value, { stream: true });
    let i;
    while ((i = buf.indexOf('\n\n')) >= 0) {
      const block = buf.slice(0, i);
      buf = buf.slice(i + 2);
      let ev = 'message', data = '';
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) ev = line.slice(6).trim();
        else if (line.startsWith('data:')) data += line.slice(5).replace(/^ /, '');
      }
      if (handlers[ev] && data) handlers[ev](JSON.parse(data));
    }
  }
}

function createChat(root, cfg) {
  const hist = [];
  let lang = cfg.voice ? (localStorage.getItem('hub.lang') || 'fr') : 'en';
  let rec = null, ctrl = null, busy = false, spoken = 0;
  root.innerHTML = `<div class="chat-head"><b>${cfg.title}</b><div class="row">${cfg.voice ? `<select class="lang" title="Language"><option value="fr">Français</option><option value="en">English</option><option value="pcm">Pidgin</option></select><button class="sm ghost mute" title="Voice replies on/off">🔇</button><button class="sm ghost cog" title="Voice settings">⚙️</button>` : ''}<button class="sm ghost x" title="Close">&times;</button></div></div>
    ${cfg.voice ? `<div class="vpanel hidden">
      <label class="chk"><input type="checkbox" class="v-on"> Read answers aloud</label>
      <label>Voice<select class="v-voice"></select></label>
      <label>Volume <span class="v-vol-n"></span><input type="range" min="0" max="100" class="v-vol"></label>
      <label>Speed <span class="v-rate-n"></span><input type="range" min="70" max="130" class="v-rate"></label>
      <div class="row"><button type="button" class="sm v-test">▶ Test voice</button><button type="button" class="sm v-stop">■ Stop speaking</button></div>
      <p class="muted" style="margin:6px 0 0">Voices come from your browser and device. Add more in your system's language settings.</p></div>` : ''}
    <div class="log"></div><div class="chips">${cfg.chips.map(c => `<button type="button">${esc(c)}</button>`).join('')}</div>
    <form class="cform"><input class="chat-input" type="text" placeholder="${esc(cfg.placeholder)}" autocomplete="off">${cfg.voice ? '<button type="button" class="mic" title="Speak">🎤</button>' : ''}<button class="primary send">Send</button><button type="button" class="stopgen hidden">■ Stop</button></form>`;
  const log = $('.log', root), input = $('.chat-input', root), mic = $('.mic', root), sendBtn = $('.send', root), stopBtn = $('.stopgen', root);

  /* ---- messages ---- */
  const scroll = (force) => { if (force || log.scrollHeight - log.scrollTop - log.clientHeight < 90) log.scrollTop = log.scrollHeight; };
  const addBubble = (html, who) => { const d = document.createElement('div'); d.className = 'bubble ' + who; d.innerHTML = html; log.appendChild(d); scroll(true); return d; };
  const renderText = raw => cfg.markdown ? md(raw) : esc(raw).replace(/\n/g, '<br>');
  const setBusy = v => { busy = v; sendBtn.classList.toggle('hidden', v); stopBtn.classList.toggle('hidden', !v); input.disabled = false; };

  /* ---- voice output ---- */
  const voices = () => HAS_TTS ? speechSynthesis.getVoices() : [];
  const voicesFor = l => { const all = voices(); const m = all.filter(v => v.lang.toLowerCase().startsWith(LANGS[l].prefix)); return m.length ? m : all; };
  const pickVoice = l => { const list = voicesFor(l); return list.find(v => v.name === (VS.voices || {})[l === 'fr' ? 'fr' : 'en']) || list.find(v => v.default) || list[0]; };
  const stopSpeaking = () => { if (HAS_TTS) speechSynthesis.cancel(); };
  function speakText(t) {
    if (!HAS_TTS) return;
    const clean = t.replace(/https?:\S+/g, '').replace(/[*_#`>\[\]]/g, '').trim();
    if (!clean) return;
    const u = new SpeechSynthesisUtterance(clean);
    const v = pickVoice(lang);
    if (v) { u.voice = v; u.lang = v.lang; } else u.lang = lang === 'fr' ? 'fr-FR' : 'en-GB';
    u.volume = VS.volume ?? 1;
    u.rate = VS.rate ?? 1;
    speechSynthesis.speak(u);
  }
  /* speaks each finished sentence while the answer is still being written */
  function speakProgress(raw, final) {
    const rest = raw.slice(spoken);
    const re = /[\s\S]+?(?:[.!?…]+(?=\s)|\n)\s*/g;
    let m, last = 0;
    while ((m = re.exec(rest))) { speakText(m[0]); last = re.lastIndex; }
    spoken += last;
    if (final) { speakText(raw.slice(spoken)); spoken = raw.length; }
  }

  /* ---- sending ---- */
  async function send(text, fromMic) {
    text = (text || '').trim();
    if (!text || busy) return;
    setBusy(true);
    addBubble(esc(text), 'user');
    hist.push({ role: 'user', content: text });
    const b = addBubble('<span class="dots"><i></i><i></i><i></i></span>', 'ai');
    let raw = '', ticket = null;
    spoken = 0;
    stopSpeaking();
    ctrl = new AbortController();
    const speakIt = () => cfg.voice && VS.on === true;
    try {
      await streamChat(cfg.endpoint, { messages: hist, ...(cfg.voice ? { language: lang } : {}) }, {
        delta: d => { raw += d.t; b.innerHTML = renderText(raw); scroll(); if (speakIt()) speakProgress(raw, false); },
        ticket: t => { ticket = t; },
        error: e => { throw new Error(e.message); },
      }, ctrl.signal);
      if (speakIt()) speakProgress(raw, true);
      if (raw) hist.push({ role: 'assistant', content: raw });
      else if (!ticket) { b.textContent = '(no answer)'; hist.pop(); }
      if (ticket) {
        const card = document.createElement('div');
        card.className = 'ok';
        card.innerHTML = `<b>Request ${esc(ticket.reference)} sent to the IWOMI team.</b>` + (ticket.loggedIn
          ? `<p class="muted">Replies will appear in <a href="#/requests">My requests</a>.</p>`
          : `<p>Tracking code <span class="code">${esc(ticket.trackingCode)}</span></p><p class="muted">Keep this code. Log in or create an account, then open <b>My requests</b> and enter the reference and this code to follow the replies.</p><div class="row">${btn('p-login', '', 'Log in', 'primary')} ${btn('p-register', '', 'Create account')}</div>`);
        log.appendChild(card);
        scroll(true);
        if (raw && !ticket.loggedIn) hist[hist.length - 1].content += `\n(Request ${ticket.reference} was created.)`;
      }
    } catch (e) {
      if (e.name === 'AbortError') {
        if (raw) { hist.push({ role: 'assistant', content: raw }); b.insertAdjacentHTML('beforeend', ' <span class="muted">(stopped)</span>'); }
        else { b.textContent = '(stopped)'; hist.pop(); }
      } else {
        b.textContent = 'Error: ' + e.message;
        b.classList.add('err');
        hist.pop();
      }
    } finally { ctrl = null; setBusy(false); input.focus(); }
  }

  $('.x', root).onclick = () => { root.classList.add('hidden'); stopSpeaking(); };
  $('.cform', root).onsubmit = e => { e.preventDefault(); const v = input.value; input.value = ''; send(v); };
  stopBtn.onclick = () => { if (ctrl) ctrl.abort(); stopSpeaking(); };
  $('.chips', root).onclick = e => { if (e.target.tagName === 'BUTTON') send(e.target.textContent); };

  /* ---- voice controls ---- */
  if (cfg.voice) {
    const sel = $('.lang', root), panel = $('.vpanel', root), on = $('.v-on', root), vsel = $('.v-voice', root),
      vol = $('.v-vol', root), rate = $('.v-rate', root), muteBtn = $('.mute', root);
    const refreshUI = () => {
      on.checked = VS.on === true;
      muteBtn.textContent = VS.on === true ? '🔊' : '🔇';
      vol.value = Math.round((VS.volume ?? 1) * 100); $('.v-vol-n', root).textContent = vol.value + '%';
      rate.value = Math.round((VS.rate ?? 1) * 100); $('.v-rate-n', root).textContent = (rate.value / 100).toFixed(1) + 'x';
      if (!HAS_TTS) { panel.insertAdjacentHTML('afterbegin', '<p class="error">Your browser cannot read text aloud.</p>'); }
    };
    const fillVoices = () => {
      const list = voicesFor(lang), cur = pickVoice(lang);
      vsel.innerHTML = list.map(v => `<option value="${esc(v.name)}" ${cur && cur.name === v.name ? 'selected' : ''}>${esc(v.name)} (${esc(v.lang)})</option>`).join('') || '<option>No voice available</option>';
    };
    const setOn = v => { VS.on = v; saveVS(); refreshUI(); if (!v) stopSpeaking(); };
    sel.value = lang;
    sel.onchange = () => { lang = sel.value; localStorage.setItem('hub.lang', lang); fillVoices(); };
    muteBtn.onclick = () => setOn(VS.on !== true);
    on.onchange = () => setOn(on.checked);
    $('.cog', root).onclick = () => panel.classList.toggle('hidden');
    vsel.onchange = () => { VS.voices = { ...(VS.voices || {}), [lang === 'fr' ? 'fr' : 'en']: vsel.value }; saveVS(); };
    vol.oninput = () => { VS.volume = vol.value / 100; $('.v-vol-n', root).textContent = vol.value + '%'; saveVS(); };
    rate.oninput = () => { VS.rate = rate.value / 100; $('.v-rate-n', root).textContent = (rate.value / 100).toFixed(1) + 'x'; saveVS(); };
    $('.v-test', root).onclick = () => { stopSpeaking(); speakText(GREET[lang]); };
    $('.v-stop', root).onclick = stopSpeaking;
    if (HAS_TTS) speechSynthesis.onvoiceschanged = fillVoices;
    refreshUI(); fillVoices();
    mic.onclick = () => {
      if (!SR) return toast('Voice input works in Chrome, Edge or Safari. You can still type.', true);
      if (rec) { rec.stop(); return; }
      stopSpeaking();
      if (VS.on === undefined) setOn(true);
      rec = new SR();
      rec.lang = LANGS[lang].stt; rec.interimResults = false; rec.maxAlternatives = 1;
      rec.onresult = e => send(e.results[0][0].transcript, true);
      rec.onerror = e => toast('Microphone: ' + e.error, true);
      rec.onend = () => { rec = null; mic.classList.remove('rec'); };
      mic.classList.add('rec');
      rec.start();
    };
  }

  let greeted = false;
  const open = () => {
    root.classList.remove('hidden');
    if (!greeted) { greeted = true; addBubble(esc(cfg.voice ? GREET[lang] : cfg.greeting), 'ai'); }
    input.focus();
  };
  return { open, toggle: () => root.classList.contains('hidden') ? open() : root.classList.add('hidden'), send: t => send(t) };
}

/* ======================= PORTAL (visitors and customers) ======================= */
let PCHAT = null, CHAT = null, HELP_PROJECT = null, pollTimer = null;
const pview = html => { $('#pview').innerHTML = html; };

async function openAuth(mode) {
  const demo = mode === 'login' ? await api('/api/auth/demo-accounts').catch(() => ({})) : {};
  const login = mode === 'login';
  const m = $('#modal');
  m.innerHTML = `<div class="box"><h3 style="margin-top:0">${login ? 'Log in' : 'Create a customer account'}</h3><form class="stack">
    ${login ? '' : '<label>Full name<input name="fullName" required></label><label>Email<input name="email" type="email"></label>'}
    <label>Username<input name="username" required autocomplete="username"></label>
    <label>Password<input name="password" type="password" required ${login ? '' : 'minlength="8"'} autocomplete="${login ? 'current-password' : 'new-password'}"></label>
    <div class="actions"><button type="button" class="ghost" id="cancel">Cancel</button><button class="primary">${login ? 'Log in' : 'Create account'}</button></div></form>
    <p class="muted" style="margin-top:10px">${login ? 'No account yet? <a href="#" id="swap">Create a customer account</a>' : 'Already registered? <a href="#" id="swap">Log in</a>'}</p>
    ${demo.enabled ? `<hr><p class="muted"><b>Demo accounts</b> · password <code>${esc(demo.password)}</code> · click one to fill the form</p><div class="row">${(demo.accounts || []).map(a => `<button type="button" class="sm" data-u="${esc(a.username)}">${esc(a.username)} · ${esc(ROLE_LABEL[a.role] || a.role)}</button>`).join('')}</div>` : ''}</div>`;
  m.classList.add('open');
  $('#cancel', m).onclick = () => m.classList.remove('open');
  $('#swap', m).onclick = e => { e.preventDefault(); openAuth(login ? 'register' : 'login'); };
  m.querySelectorAll('[data-u]').forEach(b => { b.onclick = () => { $('[name=username]', m).value = b.dataset.u; $('[name=password]', m).value = demo.password; }; });
  $('form', m).onsubmit = async e => {
    e.preventDefault();
    const body = Object.fromEntries(new FormData(e.target));
    try {
      const res = await api(login ? '/api/auth/login' : '/api/auth/register', 'POST', body);
      localStorage.setItem('hub.token', res.token);
      location.hash = '';
      location.reload();
    } catch (err) { toast(err.message, true); }
  };
}

async function solutionsView() {
  const items = await api('/api/portal/projects');
  pview(`<section class="hero"><h1>Infrastructure for financial inclusion</h1>
      <p>IWOMI Technologies builds secure, accessible software for banks and financial institutions in Cameroon and across Africa. Explore our solutions, talk to our AI assistant (type or speak, in French, English or Pidgin), or ask our team for help.</p>
      <div class="row"><button class="primary" data-act="p-chat">✨ Ask the assistant</button><button data-act="p-help">Request help from the team</button></div></section>
    <h2>Our solutions</h2><div class="cards">${items.map(p => `<div class="pcard"><h3>${esc(p.name)}</h3>${badge(p.status)}<p>${esc(p.description)}</p>
      <div>${p.components.map(c => `<span class="badge">${esc(c.type)}${c.version ? ' v' + esc(c.version) : ''}</span>`).join('')}</div>
      <div class="row" style="margin-top:10px">${btn('p-ask', p.id, 'Ask about it', '', p.name)} ${btn('p-help-for', p.id, 'Request help')}</div></div>`).join('') || '<p class="muted">No solutions published yet.</p>'}</div>`);
}

async function helpView() {
  const items = await api('/api/portal/projects');
  pview(`<div class="card narrow"><h2 style="margin-top:0">Request help</h2><p class="muted">Ask a question, report a bug or ask for help on one of our solutions. ${USER ? 'Replies will appear in <b>My requests</b>.' : 'No account needed to send it.'}</p>
    <form class="stack" data-form="help">
      ${USER ? '' : '<label>Your name<input name="name" maxlength="200"></label><label>Email or phone (so we can reach you)<input name="contact" maxlength="200"></label>'}
      <label>Solution<select name="projectId"><option value="">General / not sure</option>${items.map(p => `<option value="${p.id}" ${String(HELP_PROJECT) === String(p.id) ? 'selected' : ''}>${esc(p.name)}</option>`).join('')}</select></label>
      <label>Type<select name="category"><option value="QUESTION">Question</option><option value="BUG">Something is not working</option><option value="HELP" selected>I need help</option></select></label>
      <label>Subject<input name="subject" required maxlength="300"></label>
      <label>Describe the situation<textarea name="description" required maxlength="4000" placeholder="What did you do, what happened, any error message?"></textarea></label>
      <button class="primary">Send request</button></form>
    ${USER ? '' : '<p class="muted">To read our replies and chat with the team, you will need to log in or create an account.</p>'}</div><div id="helpDone"></div>`);
  HELP_PROJECT = null;
}

async function requestsView() {
  const rows = await api('/api/me/tickets');
  pview(`<div class="head"><h2>My requests</h2>${btn('p-help', '', '+ New request', 'primary')}</div>` +
    table([{ h: 'Ref', f: t => `<a href="#/requests/${t.id}">${esc(t.reference)}</a>` }, { h: 'Subject', f: t => esc(t.subject) }, { h: 'Solution', f: t => esc(t.project || 'General') },
      { h: 'Status', f: t => badge(t.status) }, { h: 'Updated', f: t => fmtTime(t.updatedAt) }], rows) +
    `<form class="card stack narrow" data-form="claim" style="margin-top:18px"><b>Sent a request before logging in?</b><p class="muted">Enter its reference and tracking code to link it to your account.</p>
      <label>Reference (e.g. TCK-00012)<input name="reference" required></label><label>Tracking code<input name="code" required></label><button class="primary">Link this request</button></form>`);
}

const bubbleHtml = m => `<div class="tb ${m.authorRole === 'EXTERNAL' ? 'me' : ''}"><small>${esc(m.author)} · ${fmtTime(m.createdAt)}</small>${esc(m.body).replace(/\n/g, '<br>')}</div>`;
async function threadView(id) {
  const t = await api('/api/me/tickets/' + id);
  pview(`<div class="head"><h2>${esc(t.reference)} · ${esc(t.subject)}</h2><a href="#/requests">&larr; My requests</a></div>
    <div class="card"><div class="row">${badge(t.status)}<span class="muted">${esc(t.project || 'General')} · opened ${fmtTime(t.createdAt)}</span></div><p>${esc(t.description).replace(/\n/g, '<br>')}</p></div>
    <h3>Conversation with the IWOMI team</h3><div class="thread" id="thread"></div>
    <form class="inline" data-form="portalReply" data-id="${id}"><textarea name="body" required placeholder="Write a message to the team..."></textarea><div class="actions"><button class="primary">Send</button></div></form>`);
  const render = x => { const el = $('#thread'); if (el) el.innerHTML = x.messages.map(bubbleHtml).join('') || '<p class="muted">The team will reply here. Check back soon.</p>'; };
  render(t);
  let count = t.messages.length;
  pollTimer = setInterval(async () => {
    if (!location.hash.startsWith('#/requests/')) return clearInterval(pollTimer);
    try { const x = await api('/api/me/tickets/' + id); if (x.messages.length !== count) { count = x.messages.length; render(x); } } catch (e) { /* ignore */ }
  }, 10000);
  threadRender = render;
}
let threadRender = null;

forms.help = async f => {
  const body = { subject: f.subject.value, description: f.description.value, category: f.category.value, projectId: toLong(f.projectId.value) };
  if (USER) {
    const t = await api('/api/me/tickets', 'POST', body);
    toast('Request sent: ' + t.reference);
    location.hash = '#/requests/' + t.id;
    return;
  }
  const r = await api('/api/portal/requests', 'POST', { ...body, name: f.elements.name.value, contact: f.elements.contact.value });
  $('#helpDone').innerHTML = `<div class="ok narrow" style="margin:16px auto"><b>Request received.</b><p>Reference <span class="code">${esc(r.reference)}</span></p>
    <p>Tracking code <span class="code">${esc(r.trackingCode)}</span></p>
    <p class="muted">Save these two. Our team will reply in your request: <b>log in or create an account</b>, then open <b>My requests</b> and enter this reference and code to link it.</p>
    <div class="row">${btn('p-login', '', 'Log in', 'primary')} ${btn('p-register', '', 'Create account')}</div></div>`;
  f.reset();
};
forms.claim = async f => {
  const t = await api('/api/me/tickets/claim', 'POST', { reference: f.reference.value, code: f.code.value });
  toast('Linked: ' + t.reference);
  location.hash = '#/requests/' + t.id;
};
forms.portalReply = async f => {
  const body = f.body.value.trim();
  if (!body) return;
  await api(`/api/me/tickets/${f.dataset.id}/messages`, 'POST', { body });
  f.body.value = '';
  const x = await api('/api/me/tickets/' + f.dataset.id);
  if (threadRender) threadRender(x);
};

async function portalRoute() {
  const [page, id] = location.hash.replace(/^#\/?/, '').split('/');
  let key = ['help', 'requests'].includes(page) ? page : 'solutions';
  if (key === 'requests' && !USER) { key = 'solutions'; openAuth('login'); }
  clearInterval(pollTimer);
  const nav = [['solutions', 'Solutions'], ['help', 'Request help']].concat(USER ? [['requests', 'My requests']] : []);
  $('#pnav').innerHTML = nav.map(([k, l]) => `<a href="#/${k === 'solutions' ? '' : k}" class="${k === key ? 'on' : ''}">${l}</a>`).join('');
  try {
    if (key === 'solutions') await solutionsView();
    else if (key === 'help') await helpView();
    else await (id ? threadView(Number(id)) : requestsView());
  } catch (e) { pview(`<div class="error">${esc(e.message)}</div>`); }
}

/* ======================= BOOT ======================= */
async function route() { return isStaff() ? staffRoute() : portalRoute(); }

function bootPortal() {
  $('#portal').classList.remove('hidden');
  if (USER) {
    $('#pLogin').classList.add('hidden'); $('#pRegister').classList.add('hidden'); $('#pLogout').classList.remove('hidden');
    $('#pwho').textContent = USER.fullName;
  }
  $('#pLogin').onclick = () => openAuth('login');
  $('#pRegister').onclick = () => openAuth('register');
  $('#pLogout').onclick = logout;
  PCHAT = createChat($('#pchat'), {
    endpoint: '/api/portal/chat/stream', title: '✨ IWOMI assistant', voice: true, placeholder: 'Ask a question or describe your problem…',
    chips: ['What solutions do you offer?', 'My mobile money transfer failed, what should I do?', 'Which documents do I need for KYC?', 'I want to report a bug'],
  });
  $('#pChatBtn').onclick = () => PCHAT.toggle();
  window.addEventListener('hashchange', route);
  route();
}

async function bootStaff() {
  $('#staff').classList.remove('hidden');
  $('#who').textContent = `${USER.fullName} · ${ROLE_LABEL[USER.role]}`;
  $('#logout').onclick = logout;
  await loadData();
  CHAT = createChat($('#chat'), {
    endpoint: '/api/chat/stream', title: '✨ Hub assistant', voice: false, markdown: true, placeholder: 'Ask about projects, tickets, tasks…',
    greeting: 'Hi! Ask me about projects, partners, tickets, tasks or who is working on what. I only use what your role is allowed to see.',
    chips: ['Which open tickets are urgent?', 'What is everyone working on this week?', 'Draft a reply to the latest open ticket', 'Explain what to check when a payment callback is not received'],
  });
  $('#chatBtn').onclick = () => CHAT.toggle();
  if (can('tickets')) {
    $('#bell').onclick = () => { $('#notifPanel').classList.toggle('hidden'); renderPanel(); };
    $('#notifPanel').onclick = async e => {
      const n = e.target.closest('[data-n]');
      if (!n) return;
      await api(`/api/notifications/${n.dataset.n}/read`, 'POST');
      const [type, ref] = n.dataset.ref.split(':');
      $('#notifPanel').classList.add('hidden');
      if (type === 'TICKET') location.hash = '#/tickets/' + ref;
      loadNotifs();
    };
    loadNotifs();
    connectLive();
  } else $('#bell').classList.add('hidden');
  window.addEventListener('hashchange', route);
  await route();
}

(async function init() {
  if (TOKEN) {
    try { USER = await api('/api/auth/me'); } catch (e) { USER = null; }
  }
  try {
    if (isStaff()) await bootStaff(); else bootPortal();
  } catch (e) { document.body.insertAdjacentHTML('afterbegin', `<div class="error" style="margin:20px">${esc(e.message)}</div>`); }
})();
