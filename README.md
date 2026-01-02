# ReaderForU

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Android Studio](https://img.shields.io/badge/Android%20Studio-34-green.svg)
![Platform](https://img.shields.io/badge/platform-Android-lightgrey.svg)

ReaderForU is a professional, lightweight PDF reader application for Android. It focuses on providing a clean user interface coupled with powerful features like library management, text-to-speech, and advanced document organization.

## Key Features

- **Smart PDF Library:** Automatic scanning of device storage with support for specific folder selection via Storage Access Framework.
- **Enhanced Reader:** High-performance PDF rendering with support for Night Mode, double-tap zoom, and smooth scrolling.
- **Reading Progress:** Automatically saves your last read position for every document.
- **Text-to-Speech (TTS):** Integrated engine to extract and read PDF text aloud, perfect for accessibility and multitasking.
- **Advanced Sorting & Filtering:** Organize your library by title, author, date modified, or last read time.
- **Favourites System:** Quickly access your most important documents by marking them as favourites.
- **Security:** Seamless handling of password-protected PDF files.
- **Metadata Extraction:** Automatically retrieves document information including Author, Subject, and Page Counts.
- **Customizable UI:** Support for Light, Dark, and System themes to match your preference.

## Technologies Used

- **Language:** Java
- **PDF Rendering:** [Android PDFViewer](https://github.com/barteksc/AndroidPdfViewer)
- **Metadata & Text Extraction:** [iText 7](https://itextpdf.com/en/products/itext-7)
- **Database:** SQLite (Room-ready architecture) for fast metadata caching.
- **UI Components:** Material Design 3, SwipeRefreshLayout, ConstraintLayout.

## Getting Started

### Prerequisites

- Android 9.0 (API Level 28) or higher.
- Android Studio Ladybug or newer.

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/Taut0logy/ReaderForU.git
   ```
2. Open the project in Android Studio.
3. Build and run the application on your device or emulator.

## Project Structure

- `BrowserActivity`: The main hub for managing and browsing your PDF library.
- `ReaderActivity`: The core reading interface with TTS and display controls.
- `PDFRepository`: Handles database operations and metadata persistence.
- `ThumbnailUtils`: Manages PDF thumbnail generation and caching for a visual library experience.

## Author

**Raufun Ahsan**
- GitHub: [@Taut0logy](https://github.com/Taut0logy)
- Email: raufun.ahsan@gmail.com

## License

This project is licensed under the MIT License - see the LICENSE file for details.

---
© 2024 Taut0logy. All rights reserved.
