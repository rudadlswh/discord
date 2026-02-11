import SwiftUI

struct PrimaryButton: View {
    let title: String
    var isEnabled: Bool = true
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(AppFont.subtitle(16))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, minHeight: 52)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(isEnabled ? AppTheme.buttonPrimary : AppTheme.buttonPrimary.opacity(0.5))
                )
        }
        .disabled(!isEnabled)
    }
}

struct SecondaryButton: View {
    let title: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(AppFont.subtitle(16))
                .foregroundColor(AppTheme.lightTextPrimary)
                .frame(maxWidth: .infinity, minHeight: 52)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(AppTheme.buttonSecondaryBorder, lineWidth: 1)
                        )
                )
        }
    }
}

struct RoundedTextField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    var keyboardType: UIKeyboardType = .default
    var textContentType: UITextContentType? = nil
    var isSecure: Bool = false
    var autocapitalization: TextInputAutocapitalization = .never
    var disableAutocorrection: Bool = true

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label)
                .font(AppFont.subtitle(12))
                .foregroundColor(AppTheme.lightTextSecondary)

            if isSecure {
                SecureField(placeholder, text: $text)
                    .textContentType(textContentType)
                    .keyboardType(keyboardType)
                    .textInputAutocapitalization(autocapitalization)
                    .autocorrectionDisabled(disableAutocorrection)
                    .foregroundColor(AppTheme.lightTextPrimary)
                    .padding(.horizontal, 16)
                    .frame(height: 52)
                    .background(
                        RoundedRectangle(cornerRadius: 14).fill(AppTheme.lightSurface)
                    )
            } else {
                TextField(placeholder, text: $text)
                    .textContentType(textContentType)
                    .keyboardType(keyboardType)
                    .textInputAutocapitalization(autocapitalization)
                    .autocorrectionDisabled(disableAutocorrection)
                    .foregroundColor(AppTheme.lightTextPrimary)
                    .padding(.horizontal, 16)
                    .frame(height: 52)
                    .background(
                        RoundedRectangle(cornerRadius: 14).fill(AppTheme.lightSurface)
                    )
            }
        }
    }
}

struct PasswordField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    @State private var isVisible = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label)
                .font(AppFont.subtitle(12))
                .foregroundColor(AppTheme.lightTextSecondary)

            HStack(spacing: 10) {
                Group {
                    if isVisible {
                        TextField(placeholder, text: $text)
                    } else {
                        SecureField(placeholder, text: $text)
                    }
                }
                .textContentType(.password)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .foregroundColor(AppTheme.lightTextPrimary)

                Button {
                    isVisible.toggle()
                } label: {
                    Image(systemName: isVisible ? "eye" : "eye.slash")
                        .foregroundColor(AppTheme.lightTextSecondary)
                }
            }
            .padding(.horizontal, 16)
            .frame(height: 52)
            .background(
                RoundedRectangle(cornerRadius: 14).fill(AppTheme.lightSurface)
            )
        }
    }
}

struct SegmentedToggle: View {
    let options: [String]
    @Binding var selection: Int

    var body: some View {
        HStack(spacing: 0) {
            ForEach(options.indices, id: \.self) { index in
                Button {
                    selection = index
                } label: {
                    Text(options[index])
                        .font(AppFont.subtitle(14))
                        .foregroundColor(selection == index ? AppTheme.lightTextPrimary : AppTheme.lightTextSecondary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(selection == index ? Color.white : Color.clear)
                        )
                }
            }
        }
        .frame(height: 44)
        .padding(4)
        .background(
            RoundedRectangle(cornerRadius: 14).fill(AppTheme.lightSurface)
        )
    }
}

struct AvatarCircle: View {
    let text: String
    var size: CGFloat = 44
    var fill: Color = AppTheme.darkSurfaceAlt

    var body: some View {
        Text(text)
            .font(AppFont.caps(size * 0.36))
            .foregroundColor(AppTheme.textPrimaryDark)
            .frame(width: size, height: size)
            .background(
                Circle().fill(fill)
            )
    }
}

struct AppCard<Content: View>: View {
    var background: Color = AppTheme.darkCard
    var padding: CGFloat = 12
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(padding)
            .background(
                RoundedRectangle(cornerRadius: 16).fill(background)
            )
    }
}

struct PillButton: View {
    let title: String
    var icon: String? = nil
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(AppTheme.accentBlue)
                }
                Text(title)
                    .font(AppFont.subtitle(12))
                    .foregroundColor(AppTheme.textPrimaryDark)
            }
            .padding(.horizontal, 12)
            .frame(height: 36)
            .background(
                Capsule().fill(AppTheme.darkSurface)
            )
        }
    }
}
