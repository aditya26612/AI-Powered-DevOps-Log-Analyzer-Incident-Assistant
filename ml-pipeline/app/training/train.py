from app.bootstrap.training import TrainingBootstrap

def main():

    pipeline = TrainingBootstrap.create()

    report = pipeline.run()

    print(report)


if __name__ == "__main__":
    main()