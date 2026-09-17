import pytest

from src.ulcer_index import ulcer_index


def test_no_drawdown_has_zero_ulcer_index():
    values = [100, 110, 120, 130]

    result = ulcer_index(values)

    assert result == pytest.approx(0.0)


def test_ulcer_index_single_drawdown():
    values = [100, 90]

    result = ulcer_index(values)

    expected = ((0.0 ** 2 + (-10.0) ** 2) / 2) ** 0.5

    assert result == pytest.approx(expected)


def test_ulcer_index_multiple_drawdowns():
    values = [100, 90, 95, 80]

    result = ulcer_index(values)

    expected = (
        (
            0.0 ** 2
            + (-10.0) ** 2
            + (-5.0) ** 2
            + (-20.0) ** 2
        )
        / 4
    ) ** 0.5

    assert result == pytest.approx(expected)


def test_recovery_removes_drawdown():
    values = [100, 80, 100]

    result = ulcer_index(values)

    expected = ((0.0 ** 2 + (-20.0) ** 2 + 0.0 ** 2) / 3) ** 0.5

    assert result == pytest.approx(expected)


def test_running_high_updates():
    values = [100, 80, 120, 100]

    result = ulcer_index(values)

    expected = (
        (
            0.0 ** 2
            + (-20.0) ** 2
            + 0.0 ** 2
            + ((100 / 120) - 1.0) ** 2 * 10000
        )
        / 4
    ) ** 0.5

    assert result == pytest.approx(expected)


def test_single_observation():
    result = ulcer_index([100])

    assert result == pytest.approx(0.0)


def test_input_is_not_modified():
    values = [100, 90, 110, 95]
    original = values.copy()

    ulcer_index(values)

    assert values == original


@pytest.mark.parametrize(
    "values",
    [
        [],
        [100, 0],
        [100, -10],
    ],
)
def test_invalid_values_rejected(values):
    with pytest.raises(ValueError):
        ulcer_index(values)


@pytest.mark.parametrize(
    "values",
    [
        [100, float("nan")],
        [100, float("inf")],
        [100, float("-inf")],
        [100, "bad"],
        [100, None],
    ],
)
def test_nonfinite_or_nonnumeric_values_rejected(values):
    with pytest.raises(ValueError):
        ulcer_index(values)