# carets define highlighted elements
MODEL_PATH<caret> = 239


def some_func(para<caret>m, <caret>n=5):
    return n * 42


class Foo:
    def __init__(self, arg<caret>):
        self.arg<caret> = arg

    def print(self, smth<caret>):
        print(f"Foo: {self.arg}, also {smth}")

    def getCount(self):
        return 7


class Boo(Foo):
    def print(self, a<caret>):
        print(a)


if __name__ == "__main__":
    foo<caret> = Foo(14)
    try:
        count<caret> = foo.getCount()
    except Exception as _:
        pass
