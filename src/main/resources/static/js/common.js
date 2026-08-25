/**
 * 公共前端逻辑：手动输入动态行、Beta 自动计算、参数实时校验。
 */

// ---------- 手动输入动态行 ----------
let manualYearCounter = 0;

function initManualRows(n) {
    manualYearCounter = 0;
    const base = new Date().getFullYear();
    for (let i = 0; i < n; i++) {
        addRow(base - i);
    }
}

function addRow(year) {
    const tbody = document.getElementById('manualRows');
    if (!tbody) return;
    const y = year !== undefined ? year : (new Date().getFullYear() - manualYearCounter);
    const tr = document.createElement('tr');
    tr.innerHTML = `
        <td><input class="form-control form-control-sm" name="years" type="number" value="${y}" required style="width:90px"></td>
        <td><input class="form-control form-control-sm" name="ocf" type="number" step="any" required placeholder="经营现金流"></td>
        <td><input class="form-control form-control-sm" name="capex" type="number" step="any" required placeholder="资本开支"></td>
        <td><input class="form-control form-control-sm" name="revenue" type="number" step="any" placeholder="(可选)"></td>
        <td><input class="form-control form-control-sm" name="ebit" type="number" step="any" placeholder="(可选)"></td>
        <td><input class="form-control form-control-sm" name="pretax" type="number" step="any" placeholder="(可选)"></td>
        <td><input class="form-control form-control-sm" name="tax" type="number" step="any" placeholder="(可选)"></td>
        <td><button type="button" class="btn btn-outline-danger btn-sm" onclick="this.closest('tr').remove()">✕</button></td>`;
    tbody.appendChild(tr);
    manualYearCounter++;
}

function removeRow() {
    const tbody = document.getElementById('manualRows');
    if (tbody && tbody.rows.length > 3) {
        tbody.deleteRow(tbody.rows.length - 1);
        manualYearCounter--;
    } else {
        alert('至少保留 3 年历史数据');
    }
}

// ---------- Beta 自动计算（A股） ----------
function fetchBeta(codeInputId, betaInputId, hintId, market) {
    if (market !== 'CN') {
        if (hintId) document.getElementById(hintId).textContent = '美股请手动输入 Beta（可从理杏仁/公开数据获取）';
        return;
    }
    const code = document.getElementById(codeInputId).value.trim();
    if (!/^\d{6}$/.test(code)) {
        alert('请输入 6 位数字股票代码后再自动计算 Beta');
        return;
    }
    if (hintId) document.getElementById(hintId).textContent = '计算中...';
    fetch('/api/beta?code=' + encodeURIComponent(code))
        .then(r => r.json())
        .then(data => {
            if (data.beta != null) {
                document.getElementById(betaInputId).value = data.beta.toFixed(3);
                if (hintId) document.getElementById(hintId).textContent = 'Beta = ' + data.beta.toFixed(3) + '（3 年周线 vs 沪深300）';
            } else {
                if (hintId) document.getElementById(hintId).textContent = 'Beta 计算失败，请手动输入（' + (data.error || '数据不足') + '）';
            }
        })
        .catch(e => {
            if (hintId) document.getElementById(hintId).textContent = 'Beta 计算失败，请手动输入';
        });
}

// ---------- 参数页实时计算 CAPM 折现率 ----------
function updateCapm(rfId, betaId, erpId, outId) {
    const rf = parseFloat(document.getElementById(rfId).value) / 100 || 0;
    const beta = parseFloat(document.getElementById(betaId).value) || 0;
    const erp = parseFloat(document.getElementById(erpId).value) / 100 || 0;
    const ke = rf + beta * erp;
    const out = document.getElementById(outId);
    if (out) out.textContent = 'CAPM 折现率 = ' + (ke * 100).toFixed(2) + '%（rf + β×ERP）';
    return ke;
}