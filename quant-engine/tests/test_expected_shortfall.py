import pytest

from src.expected_shortfall import historical_expected_shortfall


def test_historical_expected_shortfall():
    returns = [-0.10, -0.05, 0.0, 0.05, 0.10]

    result = historical_expected_shortfall(
        returns,
        confidence_level=0.80,
    )

    assert result == pytest.approx(0.10)


def test_expected_shortfall_at_fifty_percent():
    returns = [-0.10, -0.05, 0.0, 0.05, 0.10]

    result = historical_expected_shortfall(
        returns,
        confidence_level=0.50,
    )

    assert result == pytest.approx(0.05)


def test_expected_shortfall_with_interpolated_quantile():
    returns = [-0.10, -0.05, 0.0, 0.05]

    result = historical_expected_shortfall(
        returns,
        confidence_level=0.75,
    )

    assert result == pytest.approx(0.10)


def test_single_observation():
    result = historical_expected_shortfall(
        [-0.05],
        confidence_level=0.95,
    )

    assert result == pytest.approx(0.05)


def test_positive_lower_tail():
    returns = [0.01, 0.02, 0.03, 0.04]

    result = historical_expected_shortfall(
        returns,
        confidence_level=0.95,
    )

    assert result == pytest.approx(-0.01)


def test_input_is_not_modified():
    returns = [-0.10, -0.05, 0.0, 0.05, 0.10]
    original = returns.copy()

    historical_expected_shortfall(
        returns,
        confidence_level=0.80,
    )

    assert returns == original


def test_empty_returns_rejected():
    with pytest.raises(ValueError):
        historical_expected_shortfall([])


@pytest.mark.parametrize(
    "confidence_level",
    [
        0.0,
        1.0,
        -0.1,
        1.1,
    ],
)
def test_invalid_confidence_level_rejected(confidence_level):
    with pytest.raises(ValueError):
        historical_expected_shortfall(
            [-0.05, 0.01],
            confidence_level=confidence_level,
        )


@pytest.mark.parametrize(
    "confidence_level",
    [
        "0.95",
        None,
    ],
)
def test_non_numeric_confidence_level_rejected(confidence_level):
    with pytest.raises(TypeError):
        historical_expected_shortfall(
            [-0.05, 0.01],
            confidence_level=confidence_level,
        )


@pytest.mark.parametrize(
    "confidence_level",
    [
        float("nan"),
        float("inf"),
        float("-inf"),
    ],
)
def test_nonfinite_confidence_level_rejected(confidence_level):
    with pytest.raises(ValueError):
        historical_expected_shortfall(
            [-0.05, 0.01],
            confidence_level=confidence_level,
        )


@pytest.mark.parametrize(
    "returns",
    [
        [-0.05, "bad"],
        [-0.05, None],
    ],
)
def test_nonnumeric_returns_rejected(returns):
    with pytest.raises(ValueError):
        historical_expected_shortfall(
            returns,
            confidence_level=0.95,
        )


@pytest.mark.parametrize(
    "returns",
    [
        [-0.05, float("nan")],
        [-0.05, float("inf")],
        [-0.05, float("-inf")],
    ],
)
def test_nonfinite_returns_rejected(returns):
    with pytest.raises(ValueError):
        historical_expected_shortfall(
            returns,
            confidence_level=0.95,
        )