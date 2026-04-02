const pet = document.getElementById("b");
const btn = document.getElementById("btn");
const bubble = document.getElementById("bubble");

const barFome = document.getElementById("bar-fome");
const barHumor = document.getElementById("bar-humor");

const valFome = document.getElementById("val-fome");
const valHumor = document.getElementById("val-humor");

let fome = 100;
let humor = 100;

function falar(texto){
  bubble.textContent = texto;
  bubble.classList.add("show");
  setTimeout(()=>bubble.classList.remove("show"),2000);
}

function atualizar(){
  barFome.style.width = fome + "%";
  barHumor.style.width = humor + "%";
  valFome.textContent = Math.round(fome);
  valHumor.textContent = Math.round(humor);
}

function loop(){
  fome -= 1;
  humor -= 0.5;

  if(fome <= 30){
    falar("Tô com fome 😢");
  }

  if(fome <= 0){
    falar("Morri... 💀");
    fome = 0;
  }

  atualizar();
}

setInterval(loop,1000);

btn.addEventListener("click",()=>{
  fome += 20;
  humor += 10;

  if(fome > 100) fome = 100;
  if(humor > 100) humor = 100;

  falar("Nhac! 😋");
  atualizar();
});

pet.addEventListener("click",()=>{
  humor += 5;
  if(humor > 100) humor = 100;

  falar("Hehe 😄");
  atualizar();
});

atualizar();
