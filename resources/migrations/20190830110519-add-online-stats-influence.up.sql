UPDATE online_statistics SET statistics=jsonb_set(statistics, '{influence}', '0');
