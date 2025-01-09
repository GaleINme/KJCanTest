# KJ Can Team Management App

## Overview

The **KJ Can Team Management App** is a robust Android application designed to optimize team management and daily operations for industrial leaders, feeders, and administrators. Built with Kotlin and integrated with Firebase Realtime Database, the app offers secure, real-time collaboration and efficient role-based task management. Whether you are an administrator managing shifts, a leader supervising operations, or a feeder tracking tasks, this app ensures seamless communication and organization.

---

## Features

### Secure Login System
- User authentication with salted SHA-256 password hashing.
- Role-based redirection to specific homepages (Leader, Feeder, Admin).
- Persistent session with daily auto-expiry for enhanced security.

### Admin Dashboard
- Create, edit, and manage user accounts within the app.
- Schedule shifts and manage team assignments.
- Automated shift rotation with background workers.
- Real-time updates to roles and shifts synced with Firebase.
- Comprehensive duty roster view filtered by shifts.

### Leader Dashboard
- Real-time roster management for current shifts.
- Restricts unauthorized assignments (e.g., assigning the "Leader" role).
- Quick access to production logs, checklists, and system updates.
- Export operational data to Excel and securely share files.

### Feeder Dashboard
- Streamlined interface for tracking daily tasks and shifts.
- Personalized view for Feeder-specific operations.

### Duty Roster Management
- Role-based view customization.
- Dynamic role updates via a user-friendly interface.
- Smooth updates with `DiffUtil` for efficient data refresh.
- Sort team members by role and username.

### Excel Integration
- Generate formatted daily production reports in Excel format.
- Share Excel files securely using Android’s FileProvider.
- Consistent report formatting through embedded templates.

### Navigation Drawer
- Dynamic menu tailored to the user’s role.
- Quick access to essential features such as settings, reports, and tools.

### Real-Time Firebase Integration
- Sync user data, roles, and shifts in real time.
- Instant updates ensure data consistency across devices.

### Advanced Utilities
- Toast notifications for real-time feedback.
- Password visibility toggle for better user experience.
- Shared preferences for secure session management.

---

## Technical Highlights

- **Programming Language**: Kotlin
- **Firebase Services**:
  - Realtime Database for user and roster management.
  - File sharing via secure URIs.
- **Modern Android Components**:
  - RecyclerView with `DiffUtil` for smooth UI updates.
  - `PeriodicWorkRequest` for scheduled background tasks.
- **UI/UX**:
  - Role-based views and navigation options.
  - Elegant, user-friendly interface with dynamic components.

---

## Target Audience

- **Administrators**: Manage shifts, roles, and teams.
- **Team Leaders**: Oversee operations and manage rosters.
- **Feeders**: Track daily tasks and roles effortlessly.

This app is a one-stop solution for operational efficiency and team management in modern industrial settings.

---

## Getting Started

1. Clone this repository:
   ```bash
   git clone https://github.com/yourusername/kj-can-team-management.git
