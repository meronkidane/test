import unittest
from fixtures.unsafe_eval import parse_object


class JsonParserTest(unittest.TestCase):
    def test_valid_object(self):
        self.assertEqual(parse_object('{"name":"Ada","count":2}'), {"name": "Ada", "count": 2})

    def test_code_remains_inert_text(self):
        self.assertEqual(parse_object('{"text":"1 + 1"}'), {"text": "1 + 1"})

    def test_invalid_inputs(self):
        for value in ('1 + 1', '[]', 'null', '{', 'x' * 4097,
                      '{"a":1,"a":2}', '{"a":NaN}', '{"a":1e400}', '{"a":-1e400}',
                      '{"nested":[1e400]}', '[' * 2000):
            with self.subTest(value=value[:20]):
                with self.assertRaises(ValueError):
                    parse_object(value)

    def test_requires_text(self):
        with self.assertRaises(TypeError):
            parse_object(None)


if __name__ == '__main__':
    unittest.main()
