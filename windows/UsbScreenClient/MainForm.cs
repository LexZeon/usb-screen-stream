namespace UsbScreenClient;

internal sealed class MainForm : Form
{
    private const string AppVersion = "v1.0.0";
    private readonly ComboBox _resolution = new();
    private readonly ComboBox _bitrate = new();
    private readonly ComboBox _fps = new();
    private readonly Button _connect = new();
    private readonly Button _disconnect = new();
    private readonly Label _status = new();
    private readonly PictureBox _display = new();

    private StreamSession? _session;
    private AdbController? _adb;
    private CancellationTokenSource? _connectCts;
    private long _frames;
    private DateTime _lastFpsUpdate = DateTime.UtcNow;

    public MainForm()
    {
        Text = $"USB Screen Client {AppVersion}";
        MinimumSize = new Size(920, 560);
        StartPosition = FormStartPosition.CenterScreen;

        BuildUi();
        FormClosing += (_, _) => DisconnectAsync().GetAwaiter().GetResult();
    }

    private void BuildUi()
    {
        TableLayoutPanel root = new()
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 2,
            BackColor = Color.FromArgb(18, 20, 22)
        };
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 58));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        Controls.Add(root);

        FlowLayoutPanel toolbar = new()
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false,
            Padding = new Padding(12, 10, 12, 8),
            BackColor = Color.FromArgb(245, 246, 248)
        };
        root.Controls.Add(toolbar, 0, 0);

        AddLabel(toolbar, "Resolution");
        ConfigureCombo(_resolution, new[] { "360x800", "540x1216", "720x1624", "640x360", "960x540", "1280x720" }, 0);
        toolbar.Controls.Add(_resolution);

        AddLabel(toolbar, "Bitrate");
        ConfigureCombo(_bitrate, new[] { "400 kbps", "600 kbps", "800 kbps", "1200 kbps", "2000 kbps" }, 1);
        toolbar.Controls.Add(_bitrate);

        AddLabel(toolbar, "FPS");
        ConfigureCombo(_fps, new[] { "24", "30", "45", "60" }, 1);
        toolbar.Controls.Add(_fps);

        _connect.Text = "Connect";
        _connect.Width = 96;
        _connect.Height = 32;
        _connect.Margin = new Padding(18, 2, 6, 0);
        _connect.Click += async (_, _) => await ConnectAsync();
        toolbar.Controls.Add(_connect);

        _disconnect.Text = "Disconnect";
        _disconnect.Width = 106;
        _disconnect.Height = 32;
        _disconnect.Margin = new Padding(0, 2, 14, 0);
        _disconnect.Enabled = false;
        _disconnect.Click += async (_, _) => await DisconnectAsync();
        toolbar.Controls.Add(_disconnect);

        _status.AutoSize = true;
        _status.Text = $"Ready ({AppVersion})";
        _status.ForeColor = Color.FromArgb(50, 55, 60);
        _status.Margin = new Padding(0, 8, 0, 0);
        toolbar.Controls.Add(_status);

        _display.Dock = DockStyle.Fill;
        _display.BackColor = Color.Black;
        _display.SizeMode = PictureBoxSizeMode.Zoom;
        root.Controls.Add(_display, 0, 1);
    }

    private static void AddLabel(Control parent, string text)
    {
        parent.Controls.Add(new Label
        {
            Text = text,
            AutoSize = true,
            Margin = new Padding(8, 8, 4, 0),
            ForeColor = Color.FromArgb(40, 45, 50)
        });
    }

    private static void ConfigureCombo(ComboBox combo, string[] values, int selectedIndex)
    {
        combo.DropDownStyle = ComboBoxStyle.DropDownList;
        combo.Items.AddRange(values);
        combo.SelectedIndex = selectedIndex;
        combo.Width = 108;
        combo.Height = 32;
        combo.Margin = new Padding(0, 3, 6, 0);
    }

    private async Task ConnectAsync()
    {
        await DisconnectAsync();
        bool connected = false;

        try
        {
            SetUiConnecting(true);
            SetStatus("Finding ADB...");

            string? adbPath = AdbController.FindAdb();
            if (adbPath == null)
            {
                throw new InvalidOperationException("ADB was not found. Install Android platform-tools or set ANDROID_HOME.");
            }

            _adb = new AdbController(adbPath);
            _connectCts = new CancellationTokenSource();

            SetStatus("Preparing USB tunnel...");
            await _adb.EnsureForwardAsync(_connectCts.Token);

            StreamOptions options = ReadOptions();
            SetStatus("Waiting for Android projection...");
            _session = await StreamSession.StartAsync(options, _connectCts.Token);
            _session.FrameReceived += OnFrameReceived;
            _session.Failed += ex => BeginInvoke(() => SetStatus(ex.Message));

            SetUiConnected(true);
            SetStatus($"Streaming {options}");
            connected = true;
        }
        catch (Exception ex)
        {
            await DisconnectAsync();
            SetStatus(ex.Message);
        }
        finally
        {
            if (!connected)
            {
                SetUiConnecting(false);
            }
        }
    }

    private async Task DisconnectAsync()
    {
        _connectCts?.Cancel();
        _connectCts?.Dispose();
        _connectCts = null;

        _session?.Dispose();
        _session = null;

        Image? old = _display.Image;
        _display.Image = null;
        old?.Dispose();

        if (_adb != null)
        {
            await _adb.RemoveForwardAsync(CancellationToken.None);
            _adb = null;
        }

        SetUiConnected(false);
        SetUiConnecting(false);
        SetStatus($"Ready ({AppVersion})");
    }

    private StreamOptions ReadOptions()
    {
        string[] parts = (_resolution.SelectedItem?.ToString() ?? "960x540").Split('x');
        int width = int.Parse(parts[0]);
        int height = int.Parse(parts[1]);
        int bitrateKbps = int.Parse((_bitrate.SelectedItem?.ToString() ?? "1200 kbps").Split(' ')[0]);
        int fps = int.Parse(_fps.SelectedItem?.ToString() ?? "30");
        int quality = bitrateKbps switch
        {
            <= 400 => 35,
            <= 600 => 45,
            <= 800 => 55,
            <= 1200 => 65,
            _ => 75
        };
        return new StreamOptions(width, height, bitrateKbps * 1000, fps, quality);
    }

    private void OnFrameReceived(byte[] jpeg)
    {
        if (IsDisposed || !IsHandleCreated)
        {
            return;
        }

        try
        {
            using MemoryStream stream = new(jpeg);
            using Image decoded = Image.FromStream(stream);
            Image image = new Bitmap(decoded);
            BeginInvoke(() =>
            {
                Image? old = _display.Image;
                _display.Image = image;
                old?.Dispose();

                long frames = Interlocked.Increment(ref _frames);
                DateTime now = DateTime.UtcNow;
                if ((now - _lastFpsUpdate).TotalMilliseconds >= 1000)
                {
                    SetStatus($"Streaming {ReadOptions()} | frames {frames}");
                    _lastFpsUpdate = now;
                }
            });
        }
        catch
        {
            // Broken JPEG frames are skipped; the next frame usually arrives immediately.
        }
    }

    private void SetUiConnecting(bool connecting)
    {
        _connect.Enabled = !connecting;
        _resolution.Enabled = !connecting;
        _bitrate.Enabled = !connecting;
        _fps.Enabled = !connecting;
    }

    private void SetUiConnected(bool connected)
    {
        _disconnect.Enabled = connected;
        _connect.Enabled = !connected;
        _resolution.Enabled = !connected;
        _bitrate.Enabled = !connected;
        _fps.Enabled = !connected;
        if (!connected && string.Equals(_status.Text, "Streaming", StringComparison.OrdinalIgnoreCase))
        {
            SetStatus("Ready");
        }
    }

    private void SetStatus(string message)
    {
        _status.Text = message;
    }
}
