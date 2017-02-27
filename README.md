# KinesiS3

Save your Amazon Kinesis streams to S3.

## Installation

Lein repl will take care of the job

## Usage

On your favourite Java environment run

    $ java -jar kinesis3.jar [args]


A built in HTTP server exists which publishes stats. Navigate to /stats to fetch the JMX stats

```json
{
    "buffer": {
        "mean": 86.03183195397729,
        "percentiles": {
            "0.75": 95.0,
            "0.95": 97.0,
            "0.99": 97.0,
            "0.999": 97.0,
            "1.0": 97.0
        },
        "records": 37,
        "std-dev": 17.889915008512315
    },
    "messages": {
        "1": 0.9592920476127114,
        "15": 0.2153056911825063,
        "5": 0.5204946987787015,
        "total": 217
    },
    "s3-upload-timing": {
        "0.75": 105810517.0,
        "0.95": 143152335.0,
        "0.99": 145046260.0,
        "0.999": 1062359443.0,
        "1.0": 1062359443.0
    },
    "s3-uploads": {
        "1": 0.17018309315050342,
        "15": 0.03053969076307665,
        "5": 0.077447811393084,
        "total": 30
    }
}
```

## Options

--port                PORT (int)
--app-name            APPLICATION NAME
--checkpoint          CHECKPOINT
--aws-key             KEY
--aws-secret          SECRET
--aws-endpoint        ENDPOINT
--aws-kinesis-stream  STREAM
--s3-bucket           BUCKET
--batch-size          SIZE (int)


```
CREATE TABLE events (id CHARACTER VARYING(1024) NOT NULL,
ts TIMESTAMP(6) WITH TIME ZONE,
received_at TIMESTAMP(6) WITH TIME ZONE,
title TEXT, url TEXT, path TEXT, referrer TEXT, utm_source TEXT, utm_campaign TEXT, utm_medium TEXT, utm_content TEXT, utm_term TEXT, args JSON, cookies JSON, form JSON, user_data JSON, user_id TEXT, ip CHARACTER VARYING(50), orig_data JSON, user_agent TEXT, PRIMARY KEY (id));

```


## License

Copyright © 2015 Bahadir Cambel

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
