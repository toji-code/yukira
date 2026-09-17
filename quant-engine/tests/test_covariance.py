import pytest

from src.covariance import sample_covariance


def test_positive_covariance():
    first = [1.0, 2.0, 3.0, 4.0]
    second = [2.0, 4.0, 6.0, 8.0]

    result = sample_covariance(first, second)

    assert result == pytest.approx(3.3333333333333335)


def test_negative_covariance():
    first = [1.0, 2.0, 3.0, 4.0]
    second = [-2.0, -4.0, -6.0, -8.0]

    result = sample_covariance(first, second)

    assert result == pytest.approx(-3.3333333333333335)


def test_zero_covariance():
    first = [-2.0, -1.0, 1.0, 2.0]
    second = [1.0, -1.0, -1.0, 1.0]

    result = sample_covariance(first, second)

    assert result == pytest.approx(0.0)


def test_covariance_is_symmetric():
    first = [0.01, 0.03, -0.02, 0.04]
    second = [0.02, 0.01, -0.01, 0.03]

    assert sample_covariance(first, second) == pytest.approx(
        sample_covariance(second, first)
    )


def test_shifted_series_same_covariance():
    first = [1.0, 2.0, 3.0, 4.0]
    second = [11.0, 12.0, 13.0, 14.0]

    result = sample_covariance(first, second)

    assert result == pytest.approx(1.6666666666666667)


def test_unequal_lengths_rejected():
    with pytest.raises(ValueError):
        sample_covariance(
            [1.0, 2.0],
            [1.0],
        )


def test_insufficient_observations_rejected():
    with pytest.raises(ValueError):
        sample_covariance(
            [1.0],
            [2.0],
        )


def test_empty_series_rejected():
    with pytest.raises(ValueError):
        sample_covariance([], [])


def test_non_finite_first_value_rejected():
    with pytest.raises(ValueError):
        sample_covariance(
            [1.0, float("nan")],
            [2.0, 3.0],
        )


def test_non_finite_second_value_rejected():
    with pytest.raises(ValueError):
        sample_covariance(
            [1.0, 2.0],
            [2.0, float("inf")],
        )