"""两阶段 DCF 估值模型核心计算逻辑。

纯函数设计：不依赖 Streamlit / 网络 / 文件系统，便于单元测试与复用。

模型口径：
- 显式预测期 10 年：前 5 年固定增长率，后 5 年线性过渡到永续增长率
- 终值采用 Gordon 增长模型：TV = FCF_n * (1 + g) / (r - g)
- 企业价值 EV = 显式期现值 + 终值现值
- 股权价值 = EV - 净债务 - 少数股东权益
- 每股内在价值 = 股权价值 / 总股本
"""

from __future__ import annotations

import numpy as np
import pandas as pd


def capm_cost_of_equity(rf: float, beta: float, erp: float) -> float:
    """CAPM 股权成本：ke = rf + beta * erp。"""
    return rf + beta * erp


def discount_factors(rate: float, years: int) -> np.ndarray:
    """折现因子序列：(1+r)^-t, t = 1..years。"""
    return (1.0 + rate) ** (-np.arange(1, years + 1, dtype=float))


def forecast_growth_path(g_first: float, g_terminal: float,
                         n_first: int = 5, n_transition: int = 5) -> np.ndarray:
    """显式期增长率序列：前 n_first 年固定 g_first，过渡期线性过渡到 g_terminal。"""
    gs = [g_first] * n_first
    for i in range(1, n_transition + 1):
        gs.append(g_first + (g_terminal - g_first) * i / n_transition)
    return np.asarray(gs, dtype=float)


def forecast_fcf(base_fcf: float, growth_path: np.ndarray) -> np.ndarray:
    """从基准 FCF 出发，按增长率序列逐年生成 FCF（允许负 FCF）。"""
    out = np.empty(len(growth_path), dtype=float)
    cur = float(base_fcf)
    for i, g in enumerate(growth_path):
        cur = cur * (1.0 + g)
        out[i] = cur
    return out


def dcf_valuation(fcf: np.ndarray, discount_rate: float, g_terminal: float) -> dict:
    """两阶段 DCF 估值。

    参数:
        fcf: 显式期各年自由现金流序列
        discount_rate: 折现率（WACC）
        g_terminal: 永续增长率，必须严格小于 discount_rate

    返回:
        pv_fcf: 显式期现值
        terminal_value: 终值（未折现）
        pv_terminal: 终值现值
        enterprise_value: 企业价值
        terminal_ratio: 终值现值占企业价值的比例
    """
    if discount_rate <= g_terminal:
        raise ValueError(
            f"折现率({discount_rate:.2%})必须大于永续增长率({g_terminal:.2%})"
        )
    n = len(fcf)
    pv_fcf = float(np.sum(fcf * discount_factors(discount_rate, n)))
    tv = fcf[-1] * (1.0 + g_terminal) / (discount_rate - g_terminal)
    pv_tv = tv * (1.0 + discount_rate) ** (-n)
    ev = pv_fcf + pv_tv
    return {
        "pv_fcf": pv_fcf,
        "terminal_value": tv,
        "pv_terminal": pv_tv,
        "enterprise_value": ev,
        "terminal_ratio": pv_tv / ev if ev != 0 else float("nan"),
    }


def equity_value_from_ev(ev: float, net_debt: float, minority_interest: float = 0.0) -> float:
    """股权价值 = 企业价值 - 净债务 - 少数股东权益。"""
    return ev - net_debt - minority_interest


def per_share_value(equity_value: float, shares_outstanding: float) -> float:
    """每股内在价值 = 股权价值 / 总股本。"""
    if shares_outstanding <= 0:
        raise ValueError("总股本必须大于 0")
    return equity_value / shares_outstanding


def full_valuation(*, base_fcf: float, g_first: float, g_terminal: float,
                   discount_rate: float, net_debt: float, shares_outstanding: float,
                   minority_interest: float = 0.0,
                   n_first: int = 5, n_transition: int = 5) -> dict:
    """完整估值流水线：预测 -> 折现 -> 股权 -> 每股。"""
    path = forecast_growth_path(g_first, g_terminal, n_first, n_transition)
    fcf = forecast_fcf(base_fcf, path)
    res = dcf_valuation(fcf, discount_rate, g_terminal)
    eq = equity_value_from_ev(res["enterprise_value"], net_debt, minority_interest)
    res["fcf_forecast"] = fcf
    res["growth_path"] = path
    res["equity_value"] = eq
    res["per_share_value"] = per_share_value(eq, shares_outstanding)
    return res


def sensitivity_matrix(*, base_fcf: float, g_first: float,
                       r_range, g_range,
                       net_debt: float, shares_outstanding: float,
                       minority_interest: float = 0.0,
                       n_first: int = 5, n_transition: int = 5) -> pd.DataFrame:
    """折现率 x 永续增长率敏感性矩阵，返回每股内在价值 DataFrame。

    行 = 折现率，列 = 永续增长率。g >= r 的非法组合返回 NaN。
    """
    rows = []
    for r in r_range:
        row = {}
        for g in g_range:
            try:
                path = forecast_growth_path(g_first, g, n_first, n_transition)
                fcf = forecast_fcf(base_fcf, path)
                res = dcf_valuation(fcf, float(r), float(g))
                eq = equity_value_from_ev(res["enterprise_value"], net_debt, minority_interest)
                row[g] = per_share_value(eq, shares_outstanding)
            except (ValueError, ZeroDivisionError):
                row[g] = np.nan
        rows.append(row)
    return pd.DataFrame(rows, index=pd.Index(r_range, name="折现率"),
                        columns=pd.Index(g_range, name="永续增长率"))