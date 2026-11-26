"""Offline scoring for one complete ten-item System Usability Scale form."""

from collections.abc import Sequence
from decimal import Decimal


def score_sus(responses: Sequence[int | None]) -> Decimal | None:
    """Return the SUS score, or None for an incomplete or invalid form."""
    if len(responses) != 10:
        return None
    if any(type(answer) is not int or not 1 <= answer <= 5 for answer in responses):
        return None
    adjusted = sum(
        answer - 1 if index % 2 == 0 else 5 - answer
        for index, answer in enumerate(responses)
    )
    return Decimal(adjusted) * Decimal("2.5")
