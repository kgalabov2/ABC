"""Download every photo in photos_remote.tsv once, politely, and pack them for the app's "Import pictures".

Usage: python3 -I tools/predownload_photos.py <download dir> [<zip file>]

Files are named exactly as the app names its downloads (Photo.cacheName: first 16 hex digits of the SHA-1 of the
address, "-", the last 60 safe characters of the file name), so an imported pack fills the app's picture folder.
Requests go out over HTTP/2 with curl (Wikimedia refuses about half of HTTP/1.1 requests), at most PER_HOUR an
hour, with a random pause before each one. The download can be stopped and started again; finished files are kept.
With a zip file name, the photos are packed as pictures/<name> entries at the end.
"""
import collections, hashlib, os, random, re, subprocess, sys, time, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REMOTE = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'photos_remote.tsv')
USER_AGENT = 'AlphabetWriter/1.0 (Android; personal language-learning app)'
PER_HOUR = 1500
GAP_S = (1.5, 3.3)  # random pause before each request: about 1 500 an hour at most, usually fewer
UNSAFE = re.compile(r'[^A-Za-z0-9._-]')


def cache_name(url):
    """The app's Photo.cacheName for a remote photo."""
    tail = UNSAFE.sub('_', url.split('/')[-1].split('?')[0])[-60:]
    return hashlib.sha1(url.encode('utf-8')).hexdigest()[:16] + '-' + tail


def is_jpeg(path):
    with open(path, 'rb') as f:
        return f.read(3) == b'\xff\xd8\xff'


def main():
    out = sys.argv[1]
    zip_path = sys.argv[2] if len(sys.argv) > 2 else None
    os.makedirs(out, exist_ok=True)
    urls = [line.split('\t')[1] for line in open(REMOTE, encoding='utf-8') if line.strip() and not line.startswith('#')]
    todo = collections.deque(u for u in urls if not os.path.exists(os.path.join(out, cache_name(u))))
    print(f'{len(urls)} photos, {len(urls) - len(todo)} already here, {len(todo)} to download', flush=True)
    sent = collections.deque()  # request times within the last hour
    tries = collections.Counter()
    done = failed = 0
    while todo:
        now = time.time()
        while sent and now - sent[0] >= 3600:
            sent.popleft()
        if len(sent) >= PER_HOUR:
            time.sleep(sent[0] + 3600 - now)
            continue
        time.sleep(random.uniform(*GAP_S))
        url = todo.popleft()
        target = os.path.join(out, cache_name(url))
        part = target + '.part'
        sent.append(time.time())
        tries[url] += 1
        result = subprocess.run(
            ['curl', '--http2', '-s', '-o', part, '-w', '%{http_code}', '-A', USER_AGENT, '--max-time', '60', url],
            capture_output=True, text=True)
        code = result.stdout.strip()
        if code == '200' and os.path.getsize(part) > 0 and is_jpeg(part):
            os.replace(part, target)
            done += 1
        else:
            if os.path.exists(part):
                os.remove(part)
            if code in ('429', '503', '000') and tries[url] < 3:
                todo.append(url)  # try again later
                if code == '429':
                    time.sleep(60)  # asked to slow down
            else:
                failed += 1
                print(f'  failed ({code}): {url}', flush=True)
        if (done + failed) % 50 == 0:
            print(f'  {done} downloaded, {failed} failed, {len(todo)} left, {len(sent)} requests in the last hour',
                  flush=True)
    print(f'finished: {done} downloaded, {failed} failed', flush=True)
    if zip_path:
        names = sorted(n for n in os.listdir(out) if not n.endswith('.part'))
        with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_STORED) as z:  # JPEGs do not compress further
            for n in names:
                z.write(os.path.join(out, n), 'pictures/' + n)
        print(f'packed {len(names)} photos into {zip_path} ({os.path.getsize(zip_path) // (1024 * 1024)} MB)', flush=True)


if __name__ == '__main__':
    main()
