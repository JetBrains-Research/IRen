import random


class Generator:
    def generate_int(self, see<caret>d: int):
        print(f"Initialize seed with {seed}")
        random.seed(seed)
        return random.randint(0, 42)


if __name__ == "__main__":
    <caret>g = Generator()
    print(g.generate_int(42))
