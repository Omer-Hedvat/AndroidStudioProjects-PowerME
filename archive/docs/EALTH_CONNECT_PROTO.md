# Health Connect Implementation Protocol

## 1. Permission Manifest
- **Read/Write**: `Steps`, `HeartRate`, `RestingHeartRate`, `SleepSession`, `HeartRateVariability`.

## 2. Integration Logic
- **Daily Worker**: Sync at 06:00 AM daily.
- **Data Mapping**:
    - If `Sleep < 7h`: Coach Carter tags the day as 'High Fatigue'.
    - If `HRV` drops > 10% below 7-day mean: Boaz flags an 'Anomalous Recovery' outlier.