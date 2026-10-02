package li.gkd.app.window

/** Win32 constants absent from the pinned JNA WinUser API. */
object WindowsConstants {
    const val SC_RESTORE = 0xf120
    const val SC_MOVE = 0xf010
    const val SC_SIZE = 0xf000
    const val MF_ENABLED = 0
    const val MF_GRAYED = 1
    const val TPM_RETURNCMD = 0x0100
    const val TPM_RIGHTBUTTON = 0x0002
    const val GWLP_WNDPROC = -4
    const val WM_NCCALCSIZE = 0x0083
    const val WM_NCHITTEST = 0x0084
    const val HTCLIENT = 1
    const val HTMAXBUTTON = 9
    const val HTTRANSPARENT = -1
    const val HTCAPTION = 2
    const val WM_NCRBUTTONUP = 0x00A5
    const val WM_NCMOUSEMOVE = 0x00A0
    const val WM_NCLBUTTONDOWN = 0x00A1
    const val WM_NCLBUTTONUP = 0x00A2
    const val WM_LBUTTONDOWN = 0x0201
    const val WM_LBUTTONUP = 0x0202
    const val WM_MOUSEMOVE = 0x0200
    const val MK_LBUTTON = 1
    const val HTTOPLEFT = 13
    const val HTTOPRIGHT = 14
    const val HTBOTTOMLEFT = 16
    const val HTBOTTOMRIGHT = 17
    const val HTLEFT = 10
    const val HTRIGHT = 11
    const val HTTOP = 12
    const val HTBOTTOM = 15
}
