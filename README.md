# 3D Size Calculator Android App

This is an Android application designed to measure object dimensions (Width & Height) using ARCore.

## Features
- **AR Measurement**: Uses ARCore to detect planes (floor) and calculate 3D distances between tapped points.
- **On-Device Computation**: No server required for basic dimensioning, ensuring low latency and privacy.
- **Reporting**: Generates a dimension report and allows sharing via Email, WhatsApp, etc.

## How it works
1. **Calibration**: The user moves the phone to let ARCore detect the floor/surface.
2. **Point Selection**: The user taps on the four corners of the box.
3. **Calculation**: The app calculates the distance between points in meters using 3D Euclidean geometry:
   `d = sqrt((x2-x1)² + (y2-y1)² + (z2-z1)²)`
4. **Reporting**: The dimensions are formatted into a report.

## Tech Stack
- **Kotlin**: Primary language.
- **Jetpack Compose**: For the modern UI.
- **ARCore**: For 3D spatial awareness and depth estimation.
- **FileProvider**: For secure sharing of generated reports.

## Setup
1. Open this folder in **Android Studio**.
2. Build the project using Gradle.
3. Deploy to an ARCore-supported Android device.

## Suggested Enhancements
- **API Integration**: If high-precision volume calculation or object recognition is needed, the `ReportGenerator` can be modified to send the image + metadata to a Python-based Flask/FastAPI server running a model like **Segment Anything (SAM)**.
- **PDF Export**: Replace the `.txt` report with a formatted PDF using the included `itext7` library.
