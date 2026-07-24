import joblib

scaler = joblib.load("artifacts/scaler.pkl")

print(type(scaler))

print("mean_ :", hasattr(scaler, "mean_"))
print("scale_:", hasattr(scaler, "scale_"))

print(vars(scaler))

