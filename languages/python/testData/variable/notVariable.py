import random


class Gener<caret>ator:
    def gene<caret>rate_int(self, seed: int):
        print(f"Initialize seed with {seed}")
        random.seed(seed)
        return random.randint(0, 42)


if __name__ == "__main__":
    g = Generator()
    print(g.generate_int(42))
