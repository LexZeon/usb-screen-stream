using System.Net;
using System.Net.Sockets;
using System.Text;

namespace UsbScreenClient;

internal sealed class StreamSession : IDisposable
{
    private const int DevicePort = 27183;
    private readonly CancellationTokenSource _cts = new();
    private readonly TcpClient _deviceClient;
    private Task? _readerTask;
    private bool _disposed;

    private StreamSession(TcpClient deviceClient)
    {
        _deviceClient = deviceClient;
    }

    public event Action<byte[]>? FrameReceived;
    public event Action<Exception>? Failed;

    public static async Task<StreamSession> StartAsync(StreamOptions options, CancellationToken cancellationToken)
    {
        TcpClient device = new() { NoDelay = true, ReceiveBufferSize = 256 * 1024 };
        await ConnectWithRetryAsync(device, cancellationToken);

        NetworkStream deviceStream = device.GetStream();
        byte[] config = Encoding.ASCII.GetBytes(options.ToWireFormat() + "\n");
        await deviceStream.WriteAsync(config, cancellationToken);
        await deviceStream.FlushAsync(cancellationToken);

        StreamSession session = new(device);
        session._readerTask = Task.Run(() => session.ReadFramesAsync(session._cts.Token));
        return session;
    }

    private static async Task ConnectWithRetryAsync(TcpClient client, CancellationToken cancellationToken)
    {
        Exception? lastError = null;
        DateTime deadline = DateTime.UtcNow.AddSeconds(8);

        while (DateTime.UtcNow < deadline)
        {
            try
            {
                await client.ConnectAsync(IPAddress.Loopback, DevicePort, cancellationToken);
                return;
            }
            catch (Exception ex) when (ex is SocketException or IOException)
            {
                lastError = ex;
                await Task.Delay(300, cancellationToken);
            }
        }

        throw new InvalidOperationException(
            "Could not connect to the Android streaming service. Open the Android app, tap Start projection, and accept the screen capture prompt.",
            lastError);
    }

    private async Task ReadFramesAsync(CancellationToken cancellationToken)
    {
        try
        {
            await using NetworkStream source = _deviceClient.GetStream();
            byte[] header = new byte[4];

            while (!cancellationToken.IsCancellationRequested)
            {
                if (!await ReadExactAsync(source, header, cancellationToken))
                {
                    break;
                }

                int length = (header[0] << 24) | (header[1] << 16) | (header[2] << 8) | header[3];
                if (length <= 0 || length > 8 * 1024 * 1024)
                {
                    throw new InvalidOperationException($"Invalid frame size: {length}");
                }

                byte[] jpeg = new byte[length];
                if (!await ReadExactAsync(source, jpeg, cancellationToken))
                {
                    break;
                }

                FrameReceived?.Invoke(jpeg);
            }
        }
        catch (OperationCanceledException)
        {
        }
        catch (ObjectDisposedException)
        {
        }
        catch (Exception ex)
        {
            Failed?.Invoke(ex);
        }
    }

    private static async Task<bool> ReadExactAsync(Stream stream, byte[] buffer, CancellationToken cancellationToken)
    {
        int offset = 0;
        while (offset < buffer.Length)
        {
            int read = await stream.ReadAsync(buffer.AsMemory(offset, buffer.Length - offset), cancellationToken);
            if (read == 0)
            {
                return false;
            }
            offset += read;
        }
        return true;
    }

    public void Dispose()
    {
        if (_disposed)
        {
            return;
        }
        _disposed = true;

        _cts.Cancel();
        _deviceClient.Close();
        _cts.Dispose();
    }
}
