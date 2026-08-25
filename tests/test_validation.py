"""校验层单元测试：全角字符、代码格式、范围边界。"""

import pytest

from dcf.validation import (
    effective_tax_rate,
    normalize_text,
    validate_positive,
    validate_range,
    validate_stock_code,
    validate_years,
)


def test_normalize_fullwidth():
    assert normalize_text("６００５１９") == "600519"
    assert normalize_text("　３．５％　") == "3.5%"
    assert normalize_text(None) == ""


def test_stock_code_variants():
    assert validate_stock_code("600519") == "600519"
    assert validate_stock_code("sh600519") == "600519"
    assert validate_stock_code("600519.SH") == "600519"
    assert validate_stock_code("ＳＨ６００５１９") == "600519"


def test_stock_code_invalid():
    with pytest.raises(ValueError):
        validate_stock_code("abc")
    with pytest.raises(ValueError):
        validate_stock_code("60051")
    with pytest.raises(ValueError):
        validate_stock_code("")


def test_range_valid_and_invalid():
    assert validate_range("测试", "0.05", 0.0, 0.5) == pytest.approx(0.05)
    with pytest.raises(ValueError, match="必须在"):
        validate_range("测试", 0.6, 0.0, 0.5)
    with pytest.raises(ValueError, match="有限数值"):
        validate_range("测试", float("nan"), 0.0, 0.5)
    with pytest.raises(ValueError, match="不能为空"):
        validate_range("测试", "", 0.0, 0.5)


def test_positive():
    assert validate_positive("股本", 100) == 100.0
    with pytest.raises(ValueError):
        validate_positive("股本", 0)
    with pytest.raises(ValueError):
        validate_positive("股本", -5)


def test_effective_tax_rate():
    rate = effective_tax_rate([25.0, 30.0, 0.0], [100.0, 120.0, -50.0])
    assert rate == pytest.approx(0.25)  # 亏损年跳过


def test_effective_tax_rate_all_loss():
    assert effective_tax_rate([10.0], [-100.0]) is None


def test_years():
    assert validate_years(["2020", "2021", "2022"]) == [2020, 2021, 2022]
    with pytest.raises(ValueError, match="连续"):
        validate_years([2020, 2022, 2023])
    with pytest.raises(ValueError, match="至少"):
        validate_years([2020, 2021])