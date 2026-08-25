"""模型核心单元测试：数值对照 + 边界条件。"""

import numpy as np
import pytest

from dcf.model import (
    capm_cost_of_equity,
    dcf_valuation,
    equity_value_from_ev,
    forecast_fcf,
    forecast_growth_path,
    full_valuation,
    per_share_value,
    sensitivity_matrix,
)


def test_capm():
    assert capm_cost_of_equity(0.02, 1.0, 0.06) == pytest.approx(0.08)
    assert capm_cost_of_equity(0.017, 0.6, 0.055) == pytest.approx(0.05)


def test_forecast_growth_path_length_and_endpoints():
    gs = forecast_growth_path(0.10, 0.02, 5, 5)
    assert len(gs) == 10
    assert gs[0] == pytest.approx(0.10)
    assert gs[4] == pytest.approx(0.10)
    assert gs[5] == pytest.approx(0.084)  # 过渡第 1 年
    assert gs[9] == pytest.approx(0.02)


def test_forecast_fcf_compounding():
    gs = np.full(3, 0.10)
    fcf = forecast_fcf(100.0, gs)
    np.testing.assert_allclose(fcf, [110.0, 121.0, 133.1])


def test_dcf_valuation_constant_fcf():
    """零增长、r=10% 时 EV 应等于 FCF/r（年金+终值分解）。"""
    fcf = np.full(10, 100.0)
    res = dcf_valuation(fcf, 0.10, 0.0)
    assert res["enterprise_value"] == pytest.approx(1000.0, rel=1e-9)
    assert 0.0 < res["terminal_ratio"] < 1.0


def test_dcf_valuation_g_must_be_less_than_r():
    fcf = np.full(10, 100.0)
    with pytest.raises(ValueError, match="必须大于"):
        dcf_valuation(fcf, 0.05, 0.05)
    with pytest.raises(ValueError, match="必须大于"):
        dcf_valuation(fcf, 0.05, 0.06)


def test_negative_fcf_allowed():
    """负 FCF 不应崩溃，终值占比可能失真但仍可计算。"""
    fcf = np.array([-100.0, 50.0, 80.0, 100.0, 120.0, 130.0, 140.0, 150.0, 160.0, 170.0])
    res = dcf_valuation(fcf, 0.10, 0.02)
    assert np.isfinite(res["enterprise_value"])


def test_equity_and_per_share():
    ev = 1000.0
    eq = equity_value_from_ev(ev, net_debt=200.0, minority_interest=50.0)
    assert eq == pytest.approx(750.0)
    assert per_share_value(eq, 100.0) == pytest.approx(7.5)
    with pytest.raises(ValueError):
        per_share_value(eq, 0.0)


def test_full_valuation():
    res = full_valuation(
        base_fcf=100.0, g_first=0.08, g_terminal=0.02,
        discount_rate=0.10, net_debt=-500.0, shares_outstanding=1_256_000_000,
        minority_interest=0.3,
    )
    assert res["fcf_forecast"].shape == (10,)
    assert res["per_share_value"] == pytest.approx(
        (res["enterprise_value"] + 500.0 - 0.3) / 1_256_000_000
    )
    assert res["per_share_value"] > 0


def test_sensitivity_matrix_shape_and_nan():
    mat = sensitivity_matrix(
        base_fcf=100.0, g_first=0.08,
        r_range=[0.08, 0.10, 0.12],
        g_range=[0.02, 0.03, 0.04],
        net_debt=0.0, shares_outstanding=100.0,
    )
    assert mat.shape == (3, 3)
    assert mat.loc[0.08, 0.04] > 0
    assert mat.notna().all().all()