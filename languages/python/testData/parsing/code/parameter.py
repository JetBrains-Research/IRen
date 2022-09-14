import random


class Generator:
    def generate_int(self, seed: int):
        print(f"Initialize seed with {see<caret>d}",
              "Bugaga")
        random.seed(seed)
        return random.randint(0, 42)


if __name__ == "__main__":
    g = Generator()
    print(g.generate_int(42))
