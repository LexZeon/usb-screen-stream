namespace UsbScreenClient;

internal sealed record StreamOptions(int Width, int Height, int Bitrate, int Fps, int Quality)
{
    public string ToWireFormat() =>
        $"mode=mjpeg;width={Width};height={Height};bitrate={Bitrate};fps={Fps};quality={Quality}";

    public override string ToString() =>
        $"{Width}x{Height}, q{Quality}, {Fps} FPS";
}
