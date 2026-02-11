import SwiftUI

struct AuthDraft {
    var useEmail = true
    var email = ""
    var phone = ""
    var username = ""
    var password = ""
    var displayName = ""
    var baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
}

enum AuthRoute: Hashable {
    case login
    case registerEmail
    case registerCredentials
    case registerDisplayName
}

struct AuthFlowView: View {
    @State private var path: [AuthRoute] = []
    @State private var draft = AuthDraft()
    var onAuthSuccess: () -> Void = {}

    var body: some View {
        NavigationStack(path: $path) {
            WelcomeView(
                onSignup: { path.append(.registerEmail) },
                onLogin: { path.append(.login) }
            )
            .navigationDestination(for: AuthRoute.self) { route in
                switch route {
                case .login:
                    LoginView(
                        onSuccess: { onAuthSuccess() }
                    )
                case .registerEmail:
                    RegisterEmailView(
                        draft: $draft,
                        onNext: { path.append(.registerCredentials) }
                    )
                case .registerCredentials:
                    RegisterCredentialsView(
                        draft: $draft,
                        onNext: { path.append(.registerDisplayName) }
                    )
                case .registerDisplayName:
                    RegisterDisplayNameView(
                        draft: $draft,
                        onComplete: { onAuthSuccess() }
                    )
                }
            }
        }
    }
}

struct WelcomeView: View {
    var onSignup: () -> Void
    var onLogin: () -> Void
    @State private var animateIn = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [AppTheme.welcomeStart, AppTheme.welcomeEnd],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            Circle()
                .fill(Color.white.opacity(0.12))
                .frame(width: 220, height: 220)
                .blur(radius: 10)
                .offset(x: -120, y: -160)

            Circle()
                .fill(AppTheme.accentBlue.opacity(0.2))
                .frame(width: 260, height: 260)
                .blur(radius: 16)
                .offset(x: 140, y: -120)

            VStack(spacing: 20) {
                VStack(spacing: 16) {
                    Text("Discord Lite")
                        .font(AppFont.caps(28))
                        .foregroundColor(AppTheme.welcomeTextPrimary)

                    Text("Welcome to Discord")
                        .font(AppFont.title(22))
                        .foregroundColor(AppTheme.welcomeTextPrimary)
                        .multilineTextAlignment(.center)

                    Text("Hang out, game, and chat. Tap below to get started.")
                        .font(AppFont.body(14))
                        .foregroundColor(AppTheme.welcomeTextSecondary)
                        .multilineTextAlignment(.center)
                }
                .opacity(animateIn ? 1 : 0)
                .offset(y: animateIn ? 0 : 12)
                .animation(.easeOut(duration: 0.6), value: animateIn)

                VStack(spacing: 12) {
                    SecondaryButton(title: "Sign up", action: onSignup)
                    PrimaryButton(title: "Log in", action: onLogin)
                }
                .opacity(animateIn ? 1 : 0)
                .offset(y: animateIn ? 0 : 16)
                .animation(.easeOut(duration: 0.6).delay(0.1), value: animateIn)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .padding(.horizontal, 24)
            .padding(.top, 72)
            .padding(.bottom, 32)
        }
        .onAppear { animateIn = true }
        .toolbar(.hidden, for: .navigationBar)
    }
}

struct LoginView: View {
    var onSuccess: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var email = ""
    @State private var password = ""
    @State private var errorMessage: String? = nil
    @State private var isLoading = false
    @State private var animateIn = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Button(action: { dismiss() }) {
                    Image(systemName: "chevron.left")
                        .foregroundColor(AppTheme.lightTextPrimary)
                        .frame(width: 40, height: 40)
                }

                Text("Welcome back!")
                    .font(AppFont.title(22))
                    .foregroundColor(AppTheme.lightTextPrimary)

                Text("Glad to see you again.")
                    .font(AppFont.body(14))
                    .foregroundColor(AppTheme.lightTextSecondary)

                RoundedTextField(
                    label: "Email or Phone",
                    placeholder: "Email or phone",
                    text: $email,
                    keyboardType: .emailAddress,
                    textContentType: .emailAddress
                )

                PasswordField(
                    label: "Password",
                    placeholder: "Password",
                    text: $password
                )

                Button("Forgot your password?") {}
                    .font(AppFont.subtitle(12))
                    .foregroundColor(AppTheme.blurple)

                if let errorMessage {
                    Text(errorMessage)
                        .font(AppFont.body(12))
                        .foregroundColor(.red)
                }

                PrimaryButton(title: isLoading ? "Logging in..." : "Log in", isEnabled: !isLoading) {
                    let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
                    if trimmedEmail.isEmpty || password.isEmpty {
                        errorMessage = "Please enter your email and password."
                        return
                    }
                    errorMessage = nil

                    let storedBaseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
                    let trimmedBaseUrl = storedBaseUrl.trimmingCharacters(in: .whitespacesAndNewlines)
                    let resolvedBaseUrl = trimmedBaseUrl.isEmpty ? AuthService.defaultBaseUrl : trimmedBaseUrl
                    AppPrefs.setBaseUrl(resolvedBaseUrl)
                    Task { @MainActor in
                        isLoading = true
                        do {
                            let payload = try await AuthService.login(
                                email: trimmedEmail,
                                password: password,
                                baseUrl: resolvedBaseUrl
                            )
                            AppPrefs.saveAuth(
                                token: payload.token,
                                userId: payload.userId,
                                username: payload.username,
                                displayName: payload.displayName
                            )
                            AppPrefs.setBaseUrl(resolvedBaseUrl)
                            CallManager.shared.registerDeviceIfNeeded()
                            onSuccess()
                        } catch {
                            errorMessage = error.localizedDescription
                        }
                        isLoading = false
                    }
                }
                .padding(.top, 4)

                Text("Or log in with a passkey")
                    .font(AppFont.body(12))
                    .foregroundColor(AppTheme.lightTextSecondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.top, 8)
            }
            .padding(24)
            .opacity(animateIn ? 1 : 0)
            .offset(y: animateIn ? 0 : 12)
            .animation(.easeOut(duration: 0.5), value: animateIn)
        }
        .background(AppTheme.lightBackground.ignoresSafeArea())
        .onAppear { animateIn = true }
        .navigationBarBackButtonHidden(true)
    }
}

struct RegisterEmailView: View {
    @Binding var draft: AuthDraft
    var onNext: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var errorMessage: String? = nil
    @State private var animateIn = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Button(action: { dismiss() }) {
                    Image(systemName: "chevron.left")
                        .foregroundColor(AppTheme.lightTextPrimary)
                        .frame(width: 40, height: 40)
                }

                Text("Enter your phone or email")
                    .font(AppFont.title(20))
                    .foregroundColor(AppTheme.lightTextPrimary)
                    .frame(maxWidth: .infinity, alignment: .center)

                SegmentedToggle(
                    options: ["Phone", "Email"],
                    selection: Binding(
                        get: { draft.useEmail ? 1 : 0 },
                        set: { draft.useEmail = $0 == 1 }
                    )
                )
                .padding(.top, 4)

                if draft.useEmail {
                    RoundedTextField(
                        label: "Email",
                        placeholder: "Email",
                        text: $draft.email,
                        keyboardType: .emailAddress,
                        textContentType: .emailAddress
                    )
                    .padding(.top, 4)
                } else {
                    RoundedTextField(
                        label: "Phone",
                        placeholder: "Phone",
                        text: $draft.phone,
                        keyboardType: .phonePad,
                        textContentType: .telephoneNumber
                    )
                    .padding(.top, 4)

                    Text("Phone sign-up is not available yet.")
                        .font(AppFont.body(12))
                        .foregroundColor(AppTheme.lightTextSecondary)
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(AppFont.body(12))
                        .foregroundColor(.red)
                }

                PrimaryButton(title: "Next") {
                    let value = draft.useEmail ? draft.email : draft.phone
                    if value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        errorMessage = draft.useEmail ? "Please enter your email." : "Please enter your phone."
                        return
                    }
                    errorMessage = nil
                    onNext()
                }
                .padding(.top, 8)
            }
            .padding(24)
            .opacity(animateIn ? 1 : 0)
            .offset(y: animateIn ? 0 : 12)
            .animation(.easeOut(duration: 0.5), value: animateIn)
        }
        .background(AppTheme.lightBackground.ignoresSafeArea())
        .onAppear {
            animateIn = true
            draft.baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
        }
        .navigationBarBackButtonHidden(true)
    }
}

struct RegisterCredentialsView: View {
    @Binding var draft: AuthDraft
    var onNext: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var errorMessage: String? = nil
    @State private var animateIn = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Button(action: { dismiss() }) {
                    Image(systemName: "chevron.left")
                        .foregroundColor(AppTheme.lightTextPrimary)
                        .frame(width: 40, height: 40)
                }

                Text("Create your account")
                    .font(AppFont.title(20))
                    .foregroundColor(AppTheme.lightTextPrimary)
                    .frame(maxWidth: .infinity, alignment: .center)

                RoundedTextField(
                    label: "Username",
                    placeholder: "Username",
                    text: $draft.username,
                    textContentType: .username
                )
                .padding(.top, 4)

                PasswordField(
                    label: "Password",
                    placeholder: "Password",
                    text: $draft.password
                )

                if let errorMessage {
                    Text(errorMessage)
                        .font(AppFont.body(12))
                        .foregroundColor(.red)
                }

                PrimaryButton(title: "Next") {
                    if draft.username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        errorMessage = "Please enter your username."
                        return
                    }
                    if draft.password.isEmpty {
                        errorMessage = "Please enter your password."
                        return
                    }
                    errorMessage = nil
                    onNext()
                }
                .padding(.top, 8)
            }
            .padding(24)
            .opacity(animateIn ? 1 : 0)
            .offset(y: animateIn ? 0 : 12)
            .animation(.easeOut(duration: 0.5), value: animateIn)
        }
        .background(AppTheme.lightBackground.ignoresSafeArea())
        .onAppear { animateIn = true }
        .navigationBarBackButtonHidden(true)
    }
}

struct RegisterDisplayNameView: View {
    @Binding var draft: AuthDraft
    var onComplete: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var errorMessage: String? = nil
    @State private var isLoading = false
    @State private var animateIn = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Button(action: { dismiss() }) {
                        Image(systemName: "chevron.left")
                            .foregroundColor(AppTheme.lightTextPrimary)
                            .frame(width: 40, height: 40)
                    }

                    Spacer()

                    Button("Skip") {
                        let fallback = draft.username.trimmingCharacters(in: .whitespacesAndNewlines)
                        if fallback.isEmpty {
                            errorMessage = "Please enter a display name."
                            return
                        }
                        submitRegister(displayName: fallback)
                    }
                    .font(AppFont.subtitle(14))
                    .foregroundColor(AppTheme.lightTextSecondary)
                    .disabled(isLoading)
                }

                Text("What is your name?")
                    .font(AppFont.title(20))
                    .foregroundColor(AppTheme.lightTextPrimary)
                    .frame(maxWidth: .infinity, alignment: .center)

                RoundedTextField(
                    label: "Display name",
                    placeholder: "Display name",
                    text: $draft.displayName,
                    textContentType: .name,
                    autocapitalization: .words,
                    disableAutocorrection: false
                )
                .padding(.top, 4)

                Text("Emoji and special characters are welcome.")
                    .font(AppFont.body(12))
                    .foregroundColor(AppTheme.lightTextHint)

                if let errorMessage {
                    Text(errorMessage)
                        .font(AppFont.body(12))
                        .foregroundColor(.red)
                }

                PrimaryButton(title: isLoading ? "Creating..." : "Next", isEnabled: !isLoading) {
                    if draft.displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        errorMessage = "Please enter a display name."
                        return
                    }
                    submitRegister(displayName: draft.displayName)
                }
                .padding(.top, 8)
            }
            .padding(24)
            .opacity(animateIn ? 1 : 0)
            .offset(y: animateIn ? 0 : 12)
            .animation(.easeOut(duration: 0.5), value: animateIn)
        }
        .background(AppTheme.lightBackground.ignoresSafeArea())
        .onAppear { animateIn = true }
        .navigationBarBackButtonHidden(true)
    }

    private func submitRegister(displayName: String) {
        let identifier = draft.useEmail ? draft.email : draft.phone
        let email = identifier.trimmingCharacters(in: .whitespacesAndNewlines)
        let username = draft.username.trimmingCharacters(in: .whitespacesAndNewlines)
        let password = draft.password
        let trimmedBaseUrl = draft.baseUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        let baseUrl = trimmedBaseUrl.isEmpty ? AuthService.defaultBaseUrl : trimmedBaseUrl

        if email.isEmpty {
            errorMessage = "Please enter your email."
            return
        }
        if username.isEmpty {
            errorMessage = "Please enter your username."
            return
        }
        if password.isEmpty {
            errorMessage = "Please enter your password."
            return
        }

        errorMessage = nil

        Task { @MainActor in
            isLoading = true
            do {
                let payload = try await AuthService.register(
                    email: email,
                    username: username,
                    displayName: displayName.trimmingCharacters(in: .whitespacesAndNewlines),
                    password: password,
                    baseUrl: baseUrl
                )
                AppPrefs.saveAuth(
                    token: payload.token,
                    userId: payload.userId,
                    username: payload.username,
                    displayName: payload.displayName
                )
                AppPrefs.setBaseUrl(baseUrl)
                CallManager.shared.registerDeviceIfNeeded()
                onComplete()
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }
}

#Preview {
    AuthFlowView()
}
