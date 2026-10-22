"""Parse a bounded JSON object without executing input as Python code."""

import json

MAX_INPUT_LENGTH = 4096


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def _reject_constant(value):
    raise ValueError("Non-finite JSON numbers are not supported")


def parse_object(payload):
    """Accept at most 4096 characters of JSON with an object at its root."""
    if not isinstance(payload, str):
        raise TypeError("Payload must be text")
    if len(payload) > MAX_INPUT_LENGTH:
        raise ValueError("Payload exceeds size limit")
    try:
        result = json.loads(payload, object_pairs_hook=_unique_object,
                            parse_constant=_reject_constant)
    except (json.JSONDecodeError, RecursionError) as error:
        raise ValueError("Invalid or excessively nested JSON") from error
    if not isinstance(result, dict):
        raise ValueError("Payload must be a JSON object")
    return result
