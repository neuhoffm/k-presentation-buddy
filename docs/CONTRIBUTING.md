# Contributing to K Presentation Buddy

Thank you for your interest in contributing! This document provides guidelines and instructions for contributing to this project.

## Code of Conduct

Be respectful, inclusive, and professional in all interactions.

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR_USERNAME/k-presentation-buddy.git`
3. Create a feature branch: `git checkout -b feature/your-feature-name`

## Development Setup

### Prerequisites
- Java 17 or later
- Gradle 8.0 or later (or use `./gradlew`)

### Building
```bash
./gradlew build
```

### Running Tests
```bash
./gradlew test
```

### Running the Plugin
```bash
./gradlew runIde
```

## Code Standards

- Follow Kotlin official code style (`kotlin.code.style=official`)
- Use 4-space indentation
- Add KDoc documentation to all public APIs
- Ensure line length does not exceed 120 characters
- All files must include the MIT license header

## Making Changes

1. Make your changes in your feature branch
2. Ensure code follows the standards above
3. Add tests for new functionality
4. Update documentation as needed
5. Commit with clear, descriptive messages

## Submitting a Pull Request

1. Push your branch to your fork
2. Open a Pull Request against `main`
3. Provide a clear description of your changes
4. Reference any related issues
5. Ensure all tests pass

## Reporting Issues

- Use GitHub Issues for bug reports and feature requests
- Include relevant details: OS, IDEA version, plugin version
- Attach error logs if applicable

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

