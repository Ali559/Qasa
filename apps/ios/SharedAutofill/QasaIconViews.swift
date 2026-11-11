import SwiftUI

extension ContentUnavailableView where Label == SwiftUI.Label<Text, QasaIconImage>, Description == Text?, Actions == EmptyView {
    /// `ContentUnavailableView(_:systemImage:description:)` with a Qasa icon: the same title,
    /// glyph and description layout, the glyph at the empty-state size.
    init(_ title: String, icon: QasaIcon, description: Text? = nil) {
        self.init {
            SwiftUI.Label {
                Text(title)
            } icon: {
                QasaIconImage(icon, size: 48)
            }
        } description: {
            description
        }
    }
}
