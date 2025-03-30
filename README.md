# Vessel Inspection App (RISQ Questions)

A comprehensive Android application for conducting and managing vessel inspections using the RISQ (Rightship Inspection Questionnaire) framework.

## Overview

This application allows marine inspectors to conduct structured vessel inspections, capture photos, add comments, and generate PDF reports. The app works offline and provides functionality to upload completed inspections to a server.

## Features

### General Features
- Create and manage multiple vessel inspections
- Intuitive section-based questionnaire format
- Progress tracking for individual sections and overall inspections
- PDF export of completed sections with photo references
- Offline operation with server upload capability

### Inspection Workflow
- **Create New Inspection**: Start a new inspection with a custom name
- **Browse Sections**: Navigate through 20+ specialized inspection sections
- **Complete Questions**: Answer mandatory and optional questions
- **Add Comments**: Include detailed observations for each question
- **Take Photos**: Capture and attach photos to specific questions
- **View Inspection Guides**: Access guidance for each inspection item

### Technical Features
- Photo capture and management
- PDF generation and viewing
- Automatic saving of responses
- Search functionality for sections
- Offline data persistence
- Server upload integration

## Screenshots

(Screenshots would be included here)

## Requirements

- Android 10.0 (API level 29) or higher
- Camera (for photo capture functionality)
- Internet connection (for uploading inspections only)

## Installation

Download and install the APK from the releases section of this repository, or build from source.

## Building from Source

1. Clone the repository
```bash
git clone https://github.com/yourusername/risq_questions.git
```

2. Open the project in Android Studio

3. Build the project
```bash
./gradlew assembleDebug
```

The APK will be generated in `app/build/outputs/apk/debug/`.

## Usage

### Creating a New Inspection
1. Launch the app
2. Tap "New Inspection" or the "+" button
3. Enter a name for the inspection
4. Tap "Start Inspection"

### Completing Sections
1. Select a section from the section list
2. Answer all relevant questions
3. Add comments where necessary
4. Take photos for questions that require visual documentation
5. Return to the section list when complete

### Exporting to PDF
1. Complete all mandatory questions in a section
2. Tap the "Export PDF" button at the bottom of the screen
3. View or share the generated PDF

### Uploading Inspections
1. From the main screen, select an inspection
2. Tap the upload icon
3. Review the upload status and copy the URL if needed

## Architecture

The app follows a modern Android architecture:
- **UI**: Jetpack Compose for a reactive user interface
- **Data Management**: Repository pattern for data operations
- **File Operations**: PDF generation, photo capture and storage
- **Network**: Basic HTTP upload integration

## Libraries Used

- **Jetpack Compose**: Modern UI toolkit
- **Material 3**: Visual design system
- **Gson**: JSON serialization/deserialization
- **AndroidX Core/Lifecycle**: Android architecture components

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

Nektarios Kontolaimakis - [nkdevelopment.net](http://nkdevelopment.net)

## Acknowledgments

- RightShip for the RISQ framework and question structure
- All contributors and testers who have helped improve this application
