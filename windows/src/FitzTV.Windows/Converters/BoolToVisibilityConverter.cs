using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Data;

namespace FitzTV.Windows.Converters;

/// <summary>true to Visible, false to Collapsed. Pass ConverterParameter="invert" to flip.</summary>
public sealed class BoolToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        var on = value is true;
        if (parameter as string == "invert") on = !on;
        return on ? Visibility.Visible : Visibility.Collapsed;
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) =>
        throw new NotSupportedException();
}
