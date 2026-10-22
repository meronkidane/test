"""Intentionally unsafe scanner fixture. Never call or merge this function."""


def evaluate_untrusted(expression):
    return eval(expression)
