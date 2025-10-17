require('dotenv').config();
const express = require('express');
const cors = require('cors');
const { Octokit } = require('@octokit/rest');

const app = express();
app.use(cors());

const token = process.env.GITHUB_TOKEN;
if (!token) {
  console.error('Environment variable GITHUB_TOKEN is required.');
  process.exit(1);
}
const octokit = new Octokit({ auth: token });

app.get('/search', async (req, res) => {
  const q = req.query.q;
  if (!q) return res.status(400).json({ error: 'Missing q parameter' });
  try {
    const result = await octokit.search.repos({ q, per_page: 20 });
    const items = result.data.items.map(repo => ({
      name: repo.full_name,
      description: repo.description,
      url: repo.html_url
    }));
    res.json({ items });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: err.message });
  }
});

const port = process.env.PORT || 3000;
app.listen(port, () => console.log(`GitHub search server listening on port ${port}`));

