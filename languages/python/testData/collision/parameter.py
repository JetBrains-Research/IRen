import random


class Generator:
    def generate_int(self, seed: int, collision=42):
        print(f"Initialize seed with {see<caret>d}")
        random.seed(seed)
        boi = "Hello :)"
        return random.randint(0, collision)


if __name__ == "__main__":
    g = Generator()
    print(g.generate_int(42))
