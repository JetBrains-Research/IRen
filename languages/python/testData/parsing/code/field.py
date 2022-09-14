class Foo:
    def __init__(self, v: int):
        self.my_field = v
        ustr = u"\u2156"
        rstr = r"\u2156"
        s = "\x34\x63\u2435"

    def print_field(self):
        print(self.my_field)


if __name__ == "__main__":
    foo = Foo(42)
    foo.my_fie<caret>ld = 239
    foo.print_field()