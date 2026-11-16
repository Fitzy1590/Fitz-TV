using FitzTV.Windows.Sports;
using FitzTV.Windows.Views;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media.Animation;
using Windows.System;

namespace FitzTV.Windows;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        Root.SizeChanged += (_, e) => Drawer.Width = e.NewSize.Width * 0.30;
        // 0 ms: no page transition animation
        ContentFrame.Navigate(typeof(DashboardPage), null, new SuppressNavigationTransitionInfo());
    }

    private void OnNavChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        if (args.SelectedItem is NavigationViewItem { Tag: "live" or "guide" })
            ContentFrame.Navigate(typeof(DashboardPage), null, new SuppressNavigationTransitionInfo());
    }

    private async void OnKeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == VirtualKey.Right && Drawer.Visibility == Visibility.Collapsed)
        {
            Drawer.Visibility = Visibility.Visible;
            e.Handled = true;
            try
            {
                var games = await EspnScores.FetchAsync();
                ScoreList.ItemsSource = games.Select(g =>
                    $"{g.Away} {g.AwayScore} - {g.HomeScore} {g.Home}   " + (g.Live ? $"Q{g.Period} {g.Clock}" : "")).ToList();
            }
            catch { ScoreList.ItemsSource = new[] { "Scores unavailable" }; }
        }
        else if (e.Key == VirtualKey.Left && Drawer.Visibility == Visibility.Visible)
        {
            Drawer.Visibility = Visibility.Collapsed;
            e.Handled = true;
        }
    }
}
