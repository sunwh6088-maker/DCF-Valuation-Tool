"""输入校验层：NFKC 归一化、代码格式、数值范围。

所有用户输入在进入模型前必须经过本层，防止低级错误导致程序崩溃。
"""

from __future__ import annotations

import unicodedata

import numpy as np


def normalize_text(value) -> str:
    """NFKC 归一化：全角转半角、兼容字符统一，去除首尾空白。"""
    if value is None:
        return ""
    return unicodedata.normalize("NFKC", str(value)).strip()


def normalize_number(value) -> float:
    """数字归一化：支持字符串形式的全角/半角数字，返回 float。"""
    if value is None or value == "":
        raise ValueError("数值不能为空")
    if isinstance(value, (int, float)):
        return float(value)
    return float(normalize_text(value))


def validate_stock_code(code) -> str:
    """A 股代码校验：6 位数字，兼容 sh/sz 前缀、后缀和全角字符。"""
    code = normalize_text(code)
    code = code.upper().replace("SH", "").replace("SZ", "").replace(".", "").replace(" ", "")
    if not code.isdigit() or len(code) != 6:
        raise ValueError(f"A股代码必须是 6 位数字（如 600519），当前输入：{code!r}")
    return code


def validate_range(name: str, value, lo: float, hi: float) -> float:
    """数值范围校验：必须是有限数值且落在 [lo, hi] 内。"""
    v = normalize_number(value)
    if not np.isfinite(v):
        raise ValueError(f"{name} 必须是有限数值，当前输入：{value!r}")
    if v < lo or v > hi:
        raise ValueError(f"{name} 必须在 {lo:.2%} ~ {hi:.2%} 之间，当前值：{v:.4%}")
    return v


def validate_positive(name: str, value) -> float:
    """正数校验：必须大于 0。"""
    v = normalize_number(value)
    if not np.isfinite(v) or v <= 0:
        raise ValueError(f"{name} 必须大于 0，当前输入：{value!r}")
    return v


def effective_tax_rate(tax_expenses, pretax_incomes) -> float:
    """历史有效税率 = 所得税费用 / 税前利润，取正分母年份的均值。

    若没有可用的年份（全部亏损），返回 None 由调用方决定。
    """
    ratios = []
    for tax, pbt in zip(tax_expenses, pretax_incomes):
        t = normalize_number(tax)
        p = normalize_number(pbt)
        if p > 0:
            ratios.append(t / p)
    if not ratios:
        return None
    return float(np.mean(ratios))


def validate_years(years: list) -> None:
    """财务年份连续性校验：必须为整数、不重复、升序。"""
    if len(years) < 3:
        raise ValueError("至少需要 3 年历史数据才能计算")
    ys = [int(normalize_number(y)) for y in years]
    for i in range(1, len(ys)):
        if ys[i] <= ys[i - 1]:
            raise ValueError(f"财务年份必须严格递增且不重复：{ys}")
        if ys[i] != ys[i - 1] + 1:
            raise ValueError(f"财务年份必须连续，发现跳年：{ys[i - 1]} -> {ys[i]}")
    return ys