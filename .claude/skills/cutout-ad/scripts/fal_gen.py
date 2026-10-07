import os, sys, json, time, urllib.request, concurrent.futures as cf
KEY = os.environ["FAL_KEY"]
OUT = "cast"  # run from the folder that contains cast/
URLS = os.path.join(OUT, "urls.json")
urls = json.load(open(URLS)) if os.path.exists(URLS) else {}

def call(endpoint, body):
    req = urllib.request.Request("https://fal.run/" + endpoint, json.dumps(body).encode(),
        {"Authorization": "Key " + KEY, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=600) as r:
        return json.load(r)

def run(job):
    name, prompt, refs, size = job["name"], job["prompt"], job.get("refs", []), job.get("size", {"width": 1024, "height": 1536})
    for attempt in range(3):
        try:
            if refs:
                body = {"prompt": prompt, "image_urls": [urls[r] for r in refs], "image_size": size, "quality": "high", "num_images": 1, "output_format": "png"}
                res = call("openai/gpt-image-2/edit", body)
            else:
                body = {"prompt": prompt, "image_size": size, "quality": "high", "num_images": 1, "output_format": "png"}
                res = call("openai/gpt-image-2", body)
            u = res["images"][0]["url"]
            data = urllib.request.urlopen(u).read()
            open(os.path.join(OUT, name + ".png"), "wb").write(data)
            return name, u
        except Exception as e:
            err = str(e)
            if hasattr(e, "read"):
                err += e.read().decode()[:300]
            print("retry", name, err); time.sleep(5)
    return name, None

jobs = json.load(open(sys.argv[1]))
with cf.ThreadPoolExecutor(6) as ex:
    for name, u in ex.map(run, jobs):
        print(name, "OK" if u else "FAILED")
        if u:
            urls[name] = u
            json.dump(urls, open(URLS, "w"), indent=1)
