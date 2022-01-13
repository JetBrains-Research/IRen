class Foo:
    def __init__(self, v: int):
        self.my_field = v

    def print_field(self):
        print(self.my_field)


if __name__ == "__main__":
    foo = Foo(42)
    foo.my_fie<caret>ld = 239
    foo.print_field()