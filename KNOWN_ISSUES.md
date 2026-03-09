# Known Issues and Technical Debt

This document tracks known issues, deprecation warnings, and technical debt items for future improvement.

## Deprecation Warnings

### 1. TerminalView Usage (PresentationBuddyService.kt)

**Location:** Lines 57, 505
**Issue:** Using deprecated `TerminalView` class
**Severity:** Warning
**Recommendation:** Migrate to `TerminalToolWindowManager` in a future release

```kotlin
// Current (deprecated):
import org.jetbrains.plugins.terminal.TerminalView
val terminalView = TerminalView.getInstance(project)

// Should migrate to:
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
val terminalManager = TerminalToolWindowManager.getInstance(project)
```

**Impact:** Low - Still functional but may break in future IntelliJ Platform versions
**Priority:** Medium - Plan for update in v1.1.0

## Future Enhancements

### Testing
- [ ] Add unit tests for InstructionModels
- [ ] Add integration tests for PresentationBuddyService
- [ ] Add UI tests for actions
- [ ] Set up test coverage reporting

### Code Quality
- [ ] Set up ktlint for automated formatting
- [ ] Add detekt for static code analysis
- [ ] Document complex algorithms with detailed comments
- [ ] Add examples in KDoc

### Features
- [ ] Add progress indicator for long-running demonstrations
- [ ] Support for pausing/resuming at specific breakpoints
- [ ] Recording and playback controls
- [ ] Export demonstration to video format
- [ ] More granular error handling and user feedback

### Documentation
- [ ] Create video tutorial
- [ ] Add example demonstration JSON files
- [ ] Create API documentation site
- [ ] Add troubleshooting guide

## Non-Critical Items

### Code Organization
- Consider splitting PresentationBuddyService into smaller services (SRP)
- Extract terminal operations to separate handler class
- Extract file operations to separate handler class

### Performance
- Profile large demonstration files for optimization opportunities
- Consider lazy loading for instruction files
- Optimize coroutine usage for better responsiveness

---

## Testing Status

### ⚠️ Unit Tests Missing

**Current State:** No test files present
**Recommendation:** Add test coverage in v1.1.0

**Proposed Test Structure:**
```
src/test/kotlin/
  └── com/advntrs/kpresentationbuddy/
      ├── InstructionModelsTest.kt
      ├── PresentationBuddyServiceTest.kt
      └── actions/
          ├── LoadInstructionsActionTest.kt
          └── ToggleDemoActionTest.kt
```

**Priority:** Medium - Not blocking for v1.0.0 release

---

## Notes

**Release Status:** ✅ All blocking issues resolved. Ready for v1.0.0 release.

The remaining deprecation warning does not block the open-source release. It is tracked here for continuous improvement and will be addressed in v1.1.0.

All items are suitable for community contributions!

---

**Last Updated:** March 6, 2026
**Version:** 1.0.0
**Status:** Production Ready


