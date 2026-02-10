import SwiftUI

enum AppTheme {
    static let blurple = Color(hex: 0x5865F2)
    static let accentBlue = Color(hex: 0x5865F2)
    static let welcomeStart = Color(hex: 0x2A2D88)
    static let welcomeEnd = Color(hex: 0x0B0D33)
    static let welcomeTextPrimary = Color.white
    static let welcomeTextSecondary = Color(hex: 0xC9D0FF)
    static let lightBackground = Color(hex: 0xF6F7FB)
    static let lightSurface = Color(hex: 0xF1F2F6)
    static let lightTextPrimary = Color(hex: 0x1F2430)
    static let lightTextSecondary = Color(hex: 0x6B7280)
    static let lightTextHint = Color(hex: 0xA0A5B1)
    static let buttonPrimary = Color(hex: 0x7B7FF2)
    static let buttonSecondaryBorder = Color(hex: 0xE2E4EF)
    static let darkBackground = Color(hex: 0x1E1F24)
    static let darkSurface = Color(hex: 0x25262D)
    static let darkSurfaceAlt = Color(hex: 0x2B2D36)
    static let darkCard = Color(hex: 0x2F313A)
    static let darkRail = Color(hex: 0x181A1F)
    static let darkBorder = Color(hex: 0x343741)
    static let textPrimaryDark = Color(hex: 0xF2F4F8)
    static let textSecondaryDark = Color(hex: 0xA7ACB8)
    static let textMutedDark = Color(hex: 0x7B8190)
    static let navInactive = Color(hex: 0x8A8F9B)
    static let navActive = Color(hex: 0x7B7FF2)
    static let profileHeaderStart = Color(hex: 0xF3A12B)
    static let profileHeaderEnd = Color(hex: 0xE56E1E)
    static let sheetBackground = Color(hex: 0x2C2E36)
}

enum AppFont {
    static func title(_ size: CGFloat) -> Font {
        .custom("AvenirNext-DemiBold", size: size)
    }

    static func subtitle(_ size: CGFloat) -> Font {
        .custom("AvenirNext-Medium", size: size)
    }

    static func body(_ size: CGFloat) -> Font {
        .custom("AvenirNext-Regular", size: size)
    }

    static func caps(_ size: CGFloat) -> Font {
        .custom("AvenirNext-Bold", size: size)
    }
}

extension Color {
    init(hex: UInt, alpha: Double = 1.0) {
        let red = Double((hex >> 16) & 0xFF) / 255.0
        let green = Double((hex >> 8) & 0xFF) / 255.0
        let blue = Double(hex & 0xFF) / 255.0
        self.init(red: red, green: green, blue: blue, opacity: alpha)
    }
}
