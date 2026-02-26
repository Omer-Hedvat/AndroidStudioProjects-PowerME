# Equipment & Inventory Logic

## 1. Plate Inventory
- **User Settings**: Allow user to toggle available plates (0.5, 1.25, 2.5, 5, 10, 15, 20, 25 kg).
- **Plate Calculator**: When a weight is entered, show a 'Plate Icon' that, when clicked, displays exactly which plates to put on each side.
- **Formula**: $PlatesPerSide = \frac{TotalWeight - BarWeight}{2}$

## 2. Barbell Profiles
- Allow user to select bar type for the exercise:
    - Standard (20kg)
    - Olympic Women's (15kg)
    - EZ-Bar (7.5kg or 10kg)
    - Custom (User enters weight)