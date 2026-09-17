import pytest

from src.correlation import pearson_correlation


def test_perfect_positive_correlation():
    first = [0.01, 0.02, 0.03, 0.04]
    second = [0.02, 0.04, 0.06, 0.08]

    result = pearson_correlation(first, second)

    assert result == pytest.approx(1.0)


def test_perfect_negative_correlation():
    first = [0.01, 0.02, 0.03, 0.04]
    second = [-0.02, -0.04, -0.06, -0.08]

    result = pearson_correlation(first, second)

    assert result == pytest.approx(-1.0)


def test_zero_correlation():
    first = [-2.0, -1.0, 1.0, 2.0]
    second = [1.0, -1.0, -1.0, 1.0]

    result = pearson_correlation(first, second)

    assert result == pytest.approx(0.0)


def test_correlation_is_symmetric():
    first = [0.01, 0.03, -0.02, 0.04]
    second = [0.02, 0.01, -0.01, 0.03]

    assert pearson_correlation(first, second) == pytest.approx(
        pearson_correlation(second, first)
    )


def test_correlation_with_shifted_series():
    first = [0.01, 0.02, 0.03, 0.04]
    second = [0.11, 0.12, 0.13, 0.14]

    result = pearson_correlation(first, second)

    assert result == pytest.approx(1.0)


def test_unequal_lengths_rejected():
    with pytest.raises(ValueError):
        pearson_correlation(
            [0.01, 0.02],
            [0.01],
        )


def test_insufficient_observations_rejected():
    with pytest.raises(ValueError):
        pearson_correlation(
            [0.01],
            [0.02],
        )


def test_empty_series_rejected():
    with pytest.raises(ValueError):
        pearson_correlation([], [])


def test_non_finite_first_return_rejected():
    with pytest.raises(ValueError):
        pearson_correlation(
            [0.01, float("nan")],
            [0.02, 0.03],
        )


def test_non_finite_second_return_rejected():
    with pytest.raises(ValueError):
        pearson_correlation(
            [0.01, 0.02],
            [0.02, float("inf")],
        )


def test_zero_variance_first_series_rejected():
    with pytest.raises(ValueError):
        pearson_correlation(
            [0.02, 0.02, 0.02],
            [0.01, 0.02, 0.03],
        )


def test_zero_variance_second_series_rejected():
    with pytest.raises(ValueError):
        pearson_correlation(
            [0.01, 0.02, 0.03],
            [0.02, 0.02, 0.02],
        )