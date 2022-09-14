# carets define highlighted elements
import numpy as np

MODEL_PATH = 239


def some_func(param, n=5):
    return n * 42


class Foo:
    def __init__(self, arg):
        self.arg<caret> = arg

    def print(self, smth):
        print(f"Foo: {self.arg}, also {smth}")

    def getCount(self):
        return 7


class Boo(Foo):
    def print(self, a):
        for <caret>i in range(0, 239):
            a += i
        print(a)


if __name__ == "__main__":
    foo = Foo(14)
    try:
        count = foo.getCount()
    except Exception as _:
        pass
