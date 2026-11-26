from decimal import Decimal
import unittest

from sus_scoring import score_sus


class SusScoringTest(unittest.TestCase):
    def test_scale_direction_and_neutral_anchor(self):
        self.assertEqual(score_sus([1, 5] * 5), Decimal("0.0"))
        self.assertEqual(score_sus([5, 1] * 5), Decimal("100.0"))
        self.assertEqual(score_sus([3] * 10), Decimal("50.0"))

    def test_mixed_responses_at_documented_target(self):
        self.assertEqual(score_sus([5, 2, 4, 3, 4, 2, 3, 3, 4, 2]), Decimal("70.0"))

    def test_incomplete_and_invalid_forms_have_no_score(self):
        self.assertIsNone(score_sus([3] * 9))
        self.assertIsNone(score_sus([3] * 11))
        for invalid in (None, 0, 6, True, 3.0, "3", "3/4"):
            with self.subTest(invalid=invalid):
                self.assertIsNone(score_sus([3] * 9 + [invalid]))


if __name__ == "__main__":
    unittest.main()
